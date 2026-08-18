# 行情頁設計

* 日期：2026-08-17
* 狀態：approved
* 範圍：Happy_Trade 的第一個垂直切片——唯讀的 BTC 行情頁，加上讓它跑得起來所需的最小骨架。

## 1. 目標

交付一個可運作的 BTC 行情頁，顯示即時價格、K 線歷史、成交量，以及一組固定的技術指標。由於此時 repository 尚無任何程式碼，這個切片同時要把 ADR-0001 宣告的最小 Docker Compose 骨架（`db`、`backend`、`frontend`）架起來。

### 成功條件

1. `docker compose up` 能把三個服務都拉起來。
2. `GET /api/market/ticker?symbol=BTCUSDT` 回傳即時價格與 24 小時統計。
3. `GET /api/market/chart?symbol=BTCUSDT&interval=1h&limit=500` 回傳 500 根 K 棒，加上索引已對齊的指標序列。
4. `http://localhost:5173` 渲染出四窗格圖表（價格、成交量、RSI、MACD），並且不需手動重新整理就會更新。
5. 指標單元測試針對固定資料集與已知預期值全部通過。

## 2. 安全限制

本專案的硬性規則是**不可直接自動下單**。這個切片用結構來保證，而不是靠約定：

* Binance 客戶端**只**呼叫公開行情端點。
* 它**不帶 API key，也不做任何請求簽章**。

沒有憑證，下單就不只是「不被允許」——而是根本做不到。任何未來引入 Binance API key 的變更都必須視為紅線變更，需要專屬 ADR 與明確審查。

## 3. 架構

三個 Docker Compose 服務，與 ADR-0001 一致：

| 服務 | 映像／技術棧 | 連接埠 | 在本切片中的角色 |
|---|---|---|---|
| `db` | postgres:16 | 5432 | 會啟動，但**後端不連它**。本切片不持久化任何行情資料。它存在是為了讓基線與 ADR-0001 相符，AI 訊號切片可以直接沿用而不必重新佈建。 |
| `backend` | Java 21 + Spring Boot | 8080 | 向 Binance 抓資料、計算指標、提供 REST。 |
| `frontend` | Node + Vite dev server | 5173 | React + TypeScript SPA。把 `/api` 代理到後端。 |

本切片的後端**不**宣告 JPA 或 datasource 相依。在還沒有資料要持久化之前就加 `spring-boot-starter-data-jpa`，會逼你設定一組沒人用的 datasource，而且會在 `db` 慢一點變健康時讓後端啟動失敗。

### 資料流

```
Browser (5173)
   |  GET /api/market/ticker   每 5 秒
   |  GET /api/market/chart    依 interval 而定
   v
Spring Boot (8080)
   |  Caffeine 快取查詢（miss -> 呼叫上游）
   v
Binance 公開 REST API
```

## 4. 後端設計

根套件：`com.happytrade.market`。

| 套件 | 職責 | 相依於 |
|---|---|---|
| `model` | `Candle`、`Ticker`、`Interval`——Java record 與一個 enum。 | — |
| `provider` | `MarketDataProvider` 介面與 `BinanceMarketDataProvider`。獨佔所有關於 Binance 傳輸格式的知識。 | `model` |
| `indicator` | `Sma`、`Ema`、`Rsi`、`Macd`。**純函數，無 Spring 相依。** | — |
| `service` | `MarketChartService`——協調抓取、暖機、計算、裁切。 | `provider`、`indicator`、`model` |
| `web` | `MarketController` 與回應 DTO。 | `service` |

`indicator` 刻意不相依任何東西。每個函數接受 `double[]` 回傳 `Double[]`，因此可以直接針對固定資料集做單元測試。AI 訊號切片會原封不動地重用這個套件，這正是它不得沾染框架相依的原因。

### 4.1 Model

```java
public record Candle(
    long time,        // Unix 秒，K 棒開盤時間
    double open,
    double high,
    double low,
    double close,
    double volume
) {}

public record Ticker(
    String symbol,
    double price,
    double changePercent24h,
    double high24h,
    double low24h,
    double volume24h,
    Instant timestamp
) {}

public enum Interval {
    ONE_MINUTE("1m"), FIVE_MINUTES("5m"), FIFTEEN_MINUTES("15m"),
    ONE_HOUR("1h"), FOUR_HOURS("4h"), ONE_DAY("1d");
}
```

`time` 用 Unix **秒**，因為那是 lightweight-charts 的原生時間格式。在邊界就轉換完，代表前端完全不用做時間運算。

### 4.2 Provider

```java
public interface MarketDataProvider {
    List<Candle> fetchCandles(String symbol, Interval interval, int limit);
    Ticker fetchTicker(String symbol);
}
```

`BinanceMarketDataProvider` 呼叫：

* K 棒：`GET https://api.binance.com/api/v3/klines?symbol={symbol}&interval={interval}&limit={limit}`
  回應是陣列的陣列；index 0 是開盤時間（毫秒），1–4 是字串型別的 OHLC，5 是字串型別的基礎資產成交量。
* Ticker：`GET https://api.binance.com/api/v3/ticker/24hr?symbol={symbol}`
  使用的回應欄位：`lastPrice`、`priceChangePercent`、`highPrice`、`lowPrice`、`volume`。

連線與讀取逾時各為 5 秒。

### 4.3 指標定義

每個函數回傳的陣列長度與輸入相同。指標在數學上未定義的位置填 `null`。這些定義具規範性——單元測試就是照它們斷言的。

**SMA(n)** — 最近 `n` 根收盤價的算術平均。index `< n-1` 為 `null`。

**EMA(n)** — 在 index `n-1` 以 `SMA(n)` 起頭，之後
`EMA_i = close_i * k + EMA_{i-1} * (1 - k)`，其中 `k = 2 / (n + 1)`。index `< n-1` 為 `null`。

用 SMA 而非第一根收盤價起頭，才能讓序列可重現；從單一收盤價起頭會讓前段數值取決於「當時剛好抓了多少歷史資料」。

**RSI(14)** — Wilder 平滑法。漲跌幅逐步計算為
`gain_i = max(0, close_i - close_{i-1})` 與 `loss_i = max(0, close_{i-1} - close_i)`。第一個
`avgGain` 與 `avgLoss` 分別是前 14 個 gain 與前 14 個 loss 的簡單平均，在 index 14 產出第一個 RSI 值。之後每個平均值各自針對自己的序列獨立平滑：

```
avgGain_i = (avgGain_{i-1} * 13 + gain_i) / 14
avgLoss_i = (avgLoss_{i-1} * 13 + loss_i) / 14
```

接著 `RSI = 100 - 100 / (1 + avgGain/avgLoss)`，對退化情況有兩道防護，依此順序檢查：

* `avgGain == 0 && avgLoss == 0`——完全平盤的窗口，`RS` 是 `0/0`、未定義。RSI 為 **50**。Wilder 的原始定義對此沒有交代，而把這個情況併進 `avgLoss == 0` 分支，會把一個停滯或缺乏流動性的市場畫在區間頂端，讀起來像是極度超買，但實際上根本沒有任何方向性壓力。
* `avgLoss == 0`（因此 `avgGain > 0`）——嚴格上漲的窗口。RSI 為 **100**。

index `< 14` 為 `null`。

**MACD(12, 26, 9)** —
`macd = EMA(12) - EMA(26)`，任一輸入為 `null` 時即為 `null`；
`signal = EMA(9)`，計算對象是**壓實後**的非 null MACD 值，以其前 9 個值的 SMA 起頭；`histogram = macd - signal`，任一為 `null` 時即為 `null`。

因為訊號線是在壓實後的序列上計算的，回傳前必須把結果**對映回原始 K 棒索引**——`signal[i]` 必須對應 `candles[i]`，而不是第 i 個非 null 的 MACD 值。搞錯會讓整條訊號線往左位移，產生從未發生過的交叉，因此有專門的對齊測試涵蓋這點。

### 4.4 暖機與對齊

指標需要顯示窗口之前的歷史資料。沒有它，圖表左緣會出現 SMA200 尚未累積足夠資料的空缺。

```
請求的 limit              = 500
WARM_UP                   = 200          （由 SMA200 決定，回看期最長）
實際抓取 limit            = 700          （Binance 單次請求上限為 1000）
指標計算範圍              = 700 根 K 棒
丟棄開頭                  = 200
回傳                      = 500 根 K 棒 + 500 元素的指標陣列
```

`WARM_UP = 200` 由 SMA200 決定。其他所有設定的指標回看期都更短（EMA60 需要 60、MACD 約 35、RSI 需要 14），所以單一常數就涵蓋全部。若日後加入回看期更長的指標，`WARM_UP` 必須加大，且 `limit` 上限必須調降，以維持 `limit + WARM_UP <= 1000`。

**對齊契約：** 每個指標陣列的長度必須恰好等於回傳的 `candles` 陣列，且任一指標的 index `i` 都對應 `candles[i]`。前端不做任何位移。這由專門的測試斷言。

如果 Binance 回傳的 K 棒少於請求數量（新上市的標的，或有資料缺口），服務會消耗現有的暖機資料，而不是犧牲顯示用的 K 棒：

```
drop = max(0, min(WARM_UP, received - limit))
```

當 `received = 700, limit = 500` 時，會丟掉完整的 200 根暖機 K 棒。當 `received = 100, limit = 500` 時完全不丟，回傳全部 100 根——顯示較少 K 棒配上部分為 `null` 的指標，好過幾乎什麼都不顯示。算不出來的指標就維持 `null`。

### 4.5 快取

Caffeine，記憶體內；圖表以 `(symbol, interval)` 為 key，ticker 以 `symbol` 為 key。

| 快取 | TTL | 理由 |
|---|---|---|
| chart | 15 秒 | 多個瀏覽器分頁同時輪詢時，不得讓上游請求倍增。 |
| ticker | 3 秒 | 低於前端 5 秒的輪詢週期，所以每次更新幾乎都能拿到新資料。 |

### 4.6 錯誤處理

| 上游狀況 | 後端回應 | 前端行為 |
|---|---|---|
| Binance 429 / 418（被限流） | 503，附帶 `retryAfter` 秒數 | 退避重試，保留最後一份好資料，顯示「資料延遲」標記 |
| Binance 451（地區封鎖） | 503，附帶明確的封鎖地區訊息 | 顯示終止性錯誤，停止重試 |
| 逾時（>5 秒） | 504 | 與被限流相同 |
| `interval` 或 `limit` 無效 | 400，附帶原因 | 視為程式錯誤；使用者無法自行修復 |

**核心原則：任何錯誤都不得清空圖表。** 前端永遠保留最後一次成功的資料，並在上面疊一個狀態指示。在盯盤的時候，一片空白遠比 30 秒前的舊資料更糟。

## 5. API 契約

### `GET /api/market/ticker`

查詢參數：`symbol`（預設 `BTCUSDT`）。

`timestamp` 是**伺服器的觀測時間**——後端收到上游回應的那一刻——不是 Binance 提供的欄位。它存在是為了讓前端在錯誤退避期間，能顯示目前價格已經多舊。

```json
{
  "symbol": "BTCUSDT",
  "price": 64312.50,
  "changePercent24h": 2.41,
  "high24h": 65100.00,
  "low24h": 62800.00,
  "volume24h": 41235.6,
  "timestamp": "2026-08-17T14:23:05Z"
}
```

### `GET /api/market/chart`

查詢參數：

| 名稱 | 預設 | 限制 |
|---|---|---|
| `symbol` | `BTCUSDT` | 非空，大寫英數 |
| `interval` | `1h` | `1m`、`5m`、`15m`、`1h`、`4h`、`1d` 其中之一 |
| `limit` | `500` | 50–800（800 + 200 暖機 = 上游上限 1000） |

```json
{
  "symbol": "BTCUSDT",
  "interval": "1h",
  "candles": [
    {
      "time": 1755440000,
      "open": 63980.1,
      "high": 64420.0,
      "low": 63910.5,
      "close": 64312.5,
      "volume": 812.34
    }
  ],
  "indicators": {
    "sma200": [64010.2],
    "ema15": [64288.1],
    "ema30": [64201.7],
    "ema45": [64150.3],
    "ema60": [64098.8],
    "rsi14": [61.7],
    "macd": {
      "macd": [128.4],
      "signal": [96.2],
      "histogram": [32.2]
    }
  }
}
```

## 6. 前端設計

```
src/features/market/
  MarketPage.tsx            版面與狀態擁有者
  types.ts                  對應後端 DTO
  api/marketApi.ts          具型別的 fetch 包裝
  hooks/useTicker.ts        5 秒輪詢
  hooks/useChartData.ts     依 interval 輪詢
  components/
    PriceHeader.tsx         價格、24 小時漲跌、高／低／量
    IntervalSelector.tsx    時間框架按鈕
    PriceChart.tsx          lightweight-charts，四窗格
    IndicatorToggles.tsx    各指標顯示開關
```

### 6.1 版面

四個垂直堆疊的窗格共用一條時間軸，讓十字準星與縮放連動：

| 窗格 | 內容 | 高度 |
|---|---|---|
| 價格 | K 線 + SMA200 + EMA15/30/45/60 | 60% |
| 成交量 | 成交量柱狀圖 | 15% |
| RSI | RSI(14) 含 30/70 參考線 | 12% |
| MACD | MACD 線、訊號線、柱狀圖 | 13% |

共用時間軸正是這個版面的重點：它讓「價格突破了，而那一刻 RSI 與 MACD 在哪」一眼就看得懂。

### 6.2 繪圖

lightweight-charts v5，使用其 Panes API——一個 chart instance 搭四個窗格，軸與十字準星的同步是免費附帶的。

**備案：** 若 v5 Panes API 實際不可行，退回 v4 時代的做法——四個獨立的 chart instance，透過 `subscribeVisibleLogicalRangeChange` 手動同步各自的 `timeScale` 範圍。這要多寫程式碼，也要小心避免同步圖表之間的回饋迴圈，所以它是次選而不是首選。

### 6.3 輪詢

輪詢週期隨 K 棒週期縮放；每 5 秒重抓一次日線毫無意義，只是徒增負載。

| Interval | 1m | 5m | 15m | 1h | 4h | 1d |
|---|---|---|---|---|---|---|
| 圖表輪詢 | 20s | 30s | 60s | 60s | 120s | 300s |

不論選哪個 interval，ticker 一律每 5 秒輪詢。

當 `document.visibilityState` 為 `hidden` 時暫停輪詢，重新可見時立刻抓一次，這樣被切到背景的分頁既不浪費上游額度，使用者回來時也不會看到過期資料。

## 7. 測試

| 對象 | 方法 |
|---|---|
| 指標 | JUnit，針對固定資料集與預先算好的預期值。包含邊界：K 棒少於週期時全為 `null`；恰好等於週期時在正確索引產出第一個值。 |
| Provider | MockWebServer 重播擷取下來的 Binance JSON。驗證解析與 §4.6 的錯誤對應表。 |
| Controller | `@WebMvcTest`，驗證參數檢核與狀態碼。 |
| 對齊契約 | 專門的測試，斷言每個指標陣列長度都等於 K 棒陣列長度。 |
| 端對端 | `docker compose up`，用 `curl` 打兩個端點，然後打開 `localhost:5173` 確認四個窗格有渲染且會更新。 |

指標測試分量最重。那是唯一一處「一個悄悄算錯的數字」會以完全合理的樣貌出現在圖表上的地方。

## 8. 範圍外（YAGNI）

本切片刻意排除：

* 多標的切換 UI——`symbol` 是參數，但 UI 固定為 BTCUSDT。
* 把 K 棒持久化到 Postgres——延後到 AI 訊號切片定義出它真正需要的粒度與保存策略為止。
* 繪圖工具。
* 使用者可自訂指標週期——週期固定為 SMA200、EMA15/30/45/60、RSI14、MACD(12,26,9)。
* WebSocket / SSE 串流——provider 介面的形狀留了日後擴充空間，但本切片用輪詢。

## 9. 治理

* **ADR-0002 — 行情資料來源與繪圖技術棧。** 依 `docs/adr/README.md`，兩個條件都成立：引入外部 API（Binance）以及引入第三方套件（lightweight-charts、Caffeine）。合併為一份 ADR，是因為這兩個決策互相耦合：選了 lightweight-charts 就代表資料必須由後端擁有，而那正是逼出真實上游資料來源的原因。這份 ADR 也必須記錄「無金鑰客戶端設計如何支撐不自動下單的紅線」。
* **CHANGELOG** — 在 `[Unreleased] / 新增` 底下新增條目。
