# Happy_Trade v1 系統架構

* 日期：2026-08-18
* 狀態：proposed
* 範圍：完整第一版——行情頁、AI 訊號頁、模擬交易、持倉與績效、風險提醒。不取代任何既有文件；把 ADR-0001 從「技術棧選擇」延伸為「模組級設計」。

## 1. 目標

定義 v1 的模組邊界、持久化模型、API 介面與建造順序，讓每個功能切片都能獨立規格化與實作，不必每次都重新談整套系統。

本文件是**設計**，不是計畫。它不含實作步驟。§9 的每個切片各自會有自己的 spec 與實作計畫。

## 2. 安全紅線

**不可直接自動下單。** v1 用結構來保證，而不是靠約定：

* 程式碼中**沒有**任何券商或交易所的下單客戶端、沒有 API key、任何地方都不做請求簽章。
* **唯一**允許的對外主機是 `api.binance.com`，而且只限它的公開行情端點。
* 本系統裡「訂單」這個詞永遠指「寫進 Postgres 的一列資料」，絕不是一個對外的 HTTP 請求。
* 自動止損／止盈平倉（§6.3）**只**作用於本地模擬帳戶的資料列。它不是紅線的例外，因為資料庫之外根本沒有東西可以讓它作用。

任何引入憑證、請求簽章或新對外主機的變更都屬於紅線變更，合併前必須有專屬 ADR 並經過明確審查。

## 3. 服務拓撲

與 ADR-0001 相同。三個 Docker Compose 服務：

| 服務 | 技術棧 | 連接埠 | 角色 |
|---|---|---|---|
| `frontend` | React + TypeScript + Vite | 5173 | SPA。把 `/api` 代理到後端。 |
| `backend` | Java 21 + Spring Boot | 8080 | 所有邏輯。資料庫的唯一擁有者。 |
| `db` | PostgreSQL 16 | 5432 | 交易、持倉與訊號歷史。 |

行情切片刻意不帶 datasource 出貨（見行情頁設計 §3）。切片 2 才引入 JPA，並首次把 `backend` 接上 `db`。那是持久層變更，因此需要專屬 ADR。

## 4. 後端模組邊界

模組化單體（modular monolith）。單一部署單位，`com.happytrade` 底下五個功能套件，各自擁有自己的 model、邏輯、持久化與 web 層。

| 模組 | 擁有 | 相依於 |
|---|---|---|
| `market` | Binance 存取、K 棒、ticker、指標計算 | — |
| `signal` | 規則式訊號產生、訊號歷史 | `market` |
| `trading` | 虛擬帳戶、持倉、交易紀錄、止損引擎 | `market`（透過 port） |
| `performance` | 勝率、累計報酬、回撤 | `trading`（唯讀） |
| `risk` | 風險規則評估與警告 | `trading`、`market`（透過 port） |

相依方向單向不可逆。`market` 不認識任何其他模組，而且必須維持這樣，因為 `market.indicator` 會被 `signal` 原封不動地重用。

### 4.1 價格 port

`trading` 與 `risk` 需要當前價格，但絕不能知道怎麼跟 Binance 溝通。它們相依於一個由 `market` 擁有的窄介面：

```java
public interface PriceSource {
    double currentPrice(String symbol);
}
```

`market` 提供唯一實作，底層是既有的 3 秒 ticker 快取。這讓相依維持單向，也代表止損引擎逐一輪詢每個持倉時，不會多打任何一次上游請求。

引入 `PriceSource` 對已出貨的行情切片來說，是一個小幅度的加法變更。

## 5. 資料模型

PostgreSQL。所有金額與數量欄位都用 `numeric(20,8)`，絕不用浮點數——`double` 會在反覆的損益運算中累積誤差，而在一個「目的就是衡量績效」的工具裡，一個會飄掉幾分錢的餘額比沒用還糟。

### 5.1 `account`

v1 只有一列，但仍建成資料表，這樣未來加第二個帳戶是「插入一列」而不是「改 schema」。

| 欄位 | 型別 | 說明 |
|---|---|---|
| `id` | bigserial PK | |
| `name` | text not null | |
| `initial_balance` | numeric(20,8) not null | 起始虛擬資金。永不變動——回撤基準線依賴它。 |
| `cash_balance` | numeric(20,8) not null | 可用現金。開倉時減少，平倉時增加。 |
| `created_at` | timestamptz not null | |

### 5.2 `position`

同時存放未平倉與已平倉的持倉。一個持倉一列，平倉時就地更新。

| 欄位 | 型別 | 說明 |
|---|---|---|
| `id` | bigserial PK | |
| `account_id` | bigint FK → `account` | |
| `symbol` | text not null | v1 為 `BTCUSDT` |
| `side` | text not null | `LONG` \| `SHORT` |
| `quantity` | numeric(20,8) not null | |
| `entry_price` | numeric(20,8) not null | |
| `stop_loss` | numeric(20,8) null | null 代表沒設止損——這正是風險提醒要抓的狀況 |
| `take_profit` | numeric(20,8) null | |
| `status` | text not null | `OPEN` \| `CLOSED` |
| `opened_at` | timestamptz not null | |
| `closed_at` | timestamptz null | |
| `exit_price` | numeric(20,8) null | 實際成交價，不是觸發價（§6.3） |
| `exit_reason` | text null | `MANUAL` \| `STOP_LOSS` \| `TAKE_PROFIT` |
| `realized_pnl` | numeric(20,8) null | 平倉時凍結。永不重算。 |

在 `(account_id, status)` 建索引——止損引擎與持倉頁每個 tick 都會讀未平倉持倉。

`realized_pnl` 選擇儲存而非即時推導，是為了讓歷史資料列在損益公式日後改變、或引入手續費模型時仍然正確。

### 5.3 `trade_event`

只增不改（append-only）。永不更新，永不刪除。

| 欄位 | 型別 | 說明 |
|---|---|---|
| `id` | bigserial PK | |
| `account_id` | bigint FK → `account` | |
| `position_id` | bigint FK → `position` | |
| `type` | text not null | `OPEN` \| `CLOSE` \| `MODIFY_PROTECTION` |
| `price` | numeric(20,8) not null | |
| `quantity` | numeric(20,8) not null | |
| `occurred_at` | timestamptz not null | |
| `note` | text null | 例如：是哪條規則觸發了平倉 |

`position` 是可變的當前狀態；`trade_event` 是稽核軌跡。兩者並存不是重複——一旦持倉列在平倉時被就地更新，「事情發生的順序」就再也還原不了，而「交易紀錄」正是 v1 必須呈現的東西之一。

### 5.4 `signal`

| 欄位 | 型別 | 說明 |
|---|---|---|
| `id` | bigserial PK | |
| `symbol` | text not null | |
| `interval` | text not null | 這個訊號是在哪個時間框架上算出來的 |
| `direction` | text not null | `LONG` \| `SHORT` \| `NEUTRAL` |
| `confidence` | numeric(5,2) not null | 0–100 |
| `horizon` | text not null | 預測時間範圍，例如 `4h` |
| `rationale` | text not null | 人看得懂的理由，由觸發的規則組出來 |
| `data_as_of` | timestamptz not null | 所使用的最後一根 K 棒的收盤時間 |
| `generated_at` | timestamptz not null | 後端實際計算的時間 |
| `rule_version` | text not null | 由哪一版規則集產生 |

在 `(symbol, interval, data_as_of, rule_version)` 上建唯一約束。

這裡有兩個欄位分量特別重。`data_as_of` 之所以跟 `generated_at` 分開，是因為使用者要看的是**資料時間**——在 14:05 用一根 14:00 收盤的 K 棒重算出來的訊號，講的是 14:00 這件事。`rule_version` 存在的理由是：如果分不出某一列是哪一版規則產生的，訊號歷史就毫無價值——沒有它，改一個門檻值就會無聲地讓所有過去的訊號失效。

唯一約束讓產生過程具備冪等性：規則式訊號是輸入的純函數，因此對同一根 K 棒重複計算不得產生重複資料列。

## 6. 功能設計

### 6.1 行情頁

已在 `2026-08-17-market-page-design.md` 規格化。無變更。

### 6.2 AI 訊號頁——規則式指標投票

訊號由既有的 `market.indicator` 套件計算。不用 LLM、不打外部 API、不需要 API key。產生器是一個純函數：

```
(candles, indicator series) -> Signal
```

每條規則投出一張帶正負號權重的票，把票加總，總分再對應到方向與信心分數。每條觸發的規則貢獻理由中的一個子句，「簡短理由」就是這樣在沒有任何語言模型的情況下產生的——所謂推理，字面上就是投了票的規則清單。

示意規則集（確切門檻與權重在切片 5 的 spec 中定案）：

| 規則 | 投票意義 |
|---|---|
| 價格在 SMA200 之上／之下 | 趨勢偏向 |
| EMA15 在 EMA60 之上／之下 | 短期趨勢 |
| RSI14 高於 70／低於 30 | 均值回歸壓力 |
| MACD 柱狀圖正負翻轉 | 動能轉折 |

三個性質讓這成為 v1 的正確選擇：它是**可重現的**（同一根 K 棒永遠得到同一個訊號，這正是 `rule_version` 有意義的前提）、**可測試的**（跟指標一樣可以針對固定資料集測試），以及**免費的**，所以能排程持續執行而沒有成本上限問題。

訊號以 60 秒排程產生並持久化。若當前 K 棒尚無訊號，讀取時也會觸發產生。因為有唯一約束，兩條路徑並行執行都是安全的。

### 6.3 模擬交易

開倉：驗證、從 `PriceSource` 取當前價、從 `cash_balance` 扣掉 `quantity × price`、插入一列 `position` 與一筆 `OPEN` 交易事件——全部在同一個 transaction 內。

平倉是鏡像操作：入帳、凍結 `realized_pnl`、設 `status = CLOSED`、插入一筆 `CLOSE` 事件。

**止損引擎。** 一個 `@Scheduled` 任務每 15 秒執行一次，載入所有未平倉持倉、依 symbol 分組、透過快取的 `PriceSource` 每個 symbol 只讀一次價格，然後把任何觸及止損或止盈的持倉平掉。

這裡有個重要的誠實原則：**成交價用觀測到的價格，不是止損價。** 輪詢意味著價格可能在兩次檢查之間跳空穿過止損。用止損價成交是那個「好看」的選擇，而它會悄悄地高估儀表板報出的每一個績效數字。用觀測價成交偶爾會顯示比止損更慘的虧損——而那正是真實市場裡會發生的事，也正是一個學習風險的工具該讓你看到的。

v1 不做：手續費、滑價模型、部分成交、槓桿、追繳。日後加入手續費模型只會改變平倉時 `realized_pnl` 的算法，已儲存的歷史資料列仍保留原值。

### 6.4 持倉與績效

所有指標都在讀取時從 `position` 資料列即時計算。不建物化統計表——個人帳戶產生的是幾百列，不是幾百萬列，而在這個規模下，一個可能跟來源資料不同步的儲存彙總值是負債。

| 指標 | 定義 |
|---|---|
| 未實現損益 | 對所有未平倉持倉，以 `PriceSource` 當前價計算 |
| 已實現損益 | 已平倉持倉的 `realized_pnl` 總和 |
| 權益 | `cash_balance` + 未平倉持倉市值 |
| 勝率 | `realized_pnl > 0` 的已平倉持倉數 ÷ 所有已平倉持倉數 |
| 累計報酬 | `(equity − initial_balance) / initial_balance` |
| 最大回撤 | 權益曲線上最大的峰谷跌幅 |

權益曲線由已平倉持倉依 `closed_at` 排序建構，起點為 `initial_balance`。這使得 v1 的回撤是**已實現**回撤：它忽略仍未平倉交易內部的未實現波動，所以一個曾經跌 40% 又回到損益兩平的持倉貢獻為零。這會低估真實回撤。要修正需要定期的權益快照，這是刻意延後的——一個附帶明確限制說明的誠實數字，好過一張還沒人決定保存策略的快照表。

### 6.5 風險提醒

三條規則，門檻在 `application.yml` 設定：

| 規則 | 檢查 |
|---|---|
| 單筆最大虧損 | `\|entry − stop\| × quantity ≤ maxLossPercent × equity` |
| 部位上限 | 未平倉名目總額 `≤ maxExposurePercent × equity` |
| 未設止損 | 未平倉持倉的 `stop_loss IS NULL` |

```yaml
happytrade:
  risk:
    max-loss-per-trade-percent: 2
    max-exposure-percent: 50
```

**這些是警告，不是阻擋。** 使用者稱它們為「提醒」，而這是一個以學習為目的的個人模擬帳戶。一個會拒絕你交易的工具，只會教你怎麼繞過這個工具；一個告訴你「這筆交易風險是帳戶的 8%」然後仍讓你下手的工具，會教你 8% 是什麼感覺。

與「驗證」的區別：現金不足、數量非正、未知 symbol 都是**錯誤**，直接拒絕。那些是不可能的狀態，不是有風險的狀態。

規則在兩個地方執行：開倉請求時，警告隨新建的持倉一併回傳；以及每次讀取持倉頁時，讓一個逐漸漂移到違規的持倉持續嘮叨，而不是只在誕生那一刻被審一次。

## 7. API 介面

| 方法 | 路徑 | 用途 |
|---|---|---|
| GET | `/api/market/ticker` | 即時價格與 24 小時統計（已出貨） |
| GET | `/api/market/chart` | K 棒與指標序列（已出貨） |
| GET | `/api/signal/latest` | 指定 symbol 與 interval 的當前訊號 |
| GET | `/api/signal/history` | 歷史訊號，由新到舊 |
| GET | `/api/account` | 餘額、權益、曝險 |
| GET | `/api/positions` | 依 `status` 過濾 |
| POST | `/api/positions` | 開倉；回傳持倉加上風險警告 |
| POST | `/api/positions/{id}/close` | 以當前價手動平倉 |
| PATCH | `/api/positions/{id}/protection` | 設定或修改止損／止盈 |
| GET | `/api/trades` | `trade_event` 紀錄 |
| GET | `/api/performance` | 勝率、報酬、回撤 |
| GET | `/api/risk/warnings` | 所有未平倉持倉的當前警告 |

表中沒有任何端點會連到券商。每一次寫入都落在 Postgres。

## 8. 前端結構

```
src/
  shared/           API client、格式化、共用型別
  components/       RiskBanner 等跨頁 UI
  features/
    market/         （已出貨）
    signal/
    trading/
    portfolio/
```

四個路由：行情 / AI 訊號 / 模擬交易 / 持倉績效。風險警告不是一個頁面——它是一個 banner 元件，渲染在交易頁與持倉頁上方，因為放在自己獨立畫面的警告是沒人會讀的警告。

傳輸維持輪詢，與行情切片一致。持倉與績效每 10 秒輪詢一次；訊號每 60 秒，與其產生排程對齊。

## 9. 建造順序

| 切片 | 內容 | 相依 |
|---|---|---|
| 1 | 行情頁 | — |
| 2 | 持久化 + 模擬交易：JPA、`account` / `position` / `trade_event`、開平倉、`PriceSource`、止損引擎 | 1 |
| 3 | 持倉與績效讀取模型 | 2 |
| 4 | 風險提醒 | 2、3 |
| 5 | AI 訊號頁 | 1 |

切片 2 最大，架構風險也最高，因為它一次引入資料庫、transaction 與排程器。切片 3 與 4 很小——它們是在已存在的 schema 上加讀取模型與規則評估。

切片 5 只相依於切片 1，在那之後任何時間點都能做。它排最後是因為：一個你無法據以行動的訊號只是展示，而要能行動就需要切片 2。

## 10. 治理

各切片落地時需要的 ADR：

| ADR | 觸發原因 | 切片 |
|---|---|---|
| 0002 | 外部 API（Binance）與第三方套件（lightweight-charts、Caffeine） | 1 |
| 0003 | 持久層與交易領域 schema | 2 |
| 0004 | 風險規則定義與「警告而非阻擋」的決策 | 4 |

ADR-0002 已在行情頁實作計畫的 Task 14 中規格化。這裡先預留 0003 與 0004，避免切片之間編號衝突。

依 `CLAUDE.md` 規定，每個切片都要更新 `CHANGELOG.md` 的 `[Unreleased]` 區塊。

## 11. v1 範圍外

* 多標的交易——schema 帶有 `symbol`，但 UI 只做 BTCUSDT。
* 多帳戶——schema 支援，UI 不開放。
* 認證——單一使用者、localhost、不做登入。
* 手續費、滑價、槓桿、保證金、部分成交。
* 對歷史資料回測規則集。
* WebSocket 串流——一律輪詢。
* 用於未實現回撤的權益快照（見 §6.4）。
