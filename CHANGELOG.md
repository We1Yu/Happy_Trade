# 變更記錄（Changelog）

本檔案記錄本專案所有值得注意的變更。

格式參考 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)，並加上本專案的約定：

- 版本區塊底下**先依日期分組**（`### YYYY-MM-DD`，新的在上），日期底下再分 `#### 新增` / `#### 變更` / `#### 修正` / `#### 移除`。
- **每一條結尾附 commit hash**，寫成 (`abc1234`)；同一次 commit 帶進多條變更時，每條都附同一個 hash。
- 日期用該變更 commit 的日期（`git log --date=short`），不是寫文件當天。

## [Unreleased]

### 2026-08-19

#### 新增

- ADR 0002「市場資料擷取與圖表技術選型」（`docs/adr/0002-market-data-and-charting-stack.md`）：記錄選擇「後端轉接 Binance Spot 公開 REST ＋ 前端 Lightweight Charts」的理由與被否決的方案。資料更新機制據實記為**前端輪詢驅動的 pull-through 快取**（後端無 `@Scheduled`），並列出目前已知的取捨：K 線未持久化、快取過期遇上游失敗無降級路徑、symbol 無白名單、WebSocket 升級門檻未定義。(`TBD`)

#### 變更

- `CHANGELOG.md` 改為依日期分組（`### YYYY-MM-DD` 底下再分 `#### 新增` / `#### 變更` / `#### 修正` / `#### 移除`），每條補上對應的 commit hash；此格式約定同步寫入 `CLAUDE.md` 的治理章節。(`TBD`)

### 2026-08-18

#### 新增

- 後端 Spring Boot 骨架（`backend/`），含健康檢查冒煙測試、Maven wrapper 與 Dockerfile。此切片不含 JPA／datasource 相依。(`946a69a`)
- 行情資料的領域模型：`Candle`（OHLCV record）、`Ticker`（價格與 24 小時統計）、`Interval` enum（時間框架代碼，支援雙向轉換）。(`41a5317`)
- SMA 與 EMA 指標（`Sma`、`Ema`），分別採移動平均與 SMA 起頭的指數平滑，暖機區間皆補 null。(`a759cfe`)
- 無外部相依的指標套件：SMA、EMA、RSI（Wilder）與 MACD，訊號線已對回原始 K 棒索引。(`c758741`)
- 無金鑰的 Binance 行情資料提供者（`BinanceMarketDataProvider`），只讀取公開的 klines 與 24hr ticker 端點，並以 `UpstreamException` 對應限流（429/418）、地區封鎖（451）與逾時。不送 API key，也不做請求簽章。(`9010d6c`)
- RSI 平盤處理：完全平盤的價格序列（RS 為 0/0）現在回傳 50（中性），不再誤判為全數上漲。(`05fa3d1`)
- 行情圖表服務（`MarketChartService`）：在請求的顯示筆數之上多抓 200 根暖機 K 棒，於完整視窗上計算 SMA200／EMA15-30-45-60／RSI14／MACD，再裁掉暖機段，讓每條指標序列與回傳的 K 棒索引完全對齊。上游回傳筆數少於顯示需求時，改為保留全部 K 棒。(`993d5e2`)
- 唯讀行情 REST 端點：`GET /api/market/ticker` 與 `GET /api/market/chart`（`MarketController`），搭配把三條 MACD 序列收攏成巢狀結構的 `ChartResponse` DTO、symbol／interval／limit 驗證（limit 50-800），以及 `@RestControllerAdvice`：參數錯誤對應 400，上游限流／封鎖／逾時分別對應 503／503／504，回傳結構化的 `ApiError`。所有端點都不下單、也不模擬下單。(`0ea9250`)
- 行情資料的 Caffeine 快取（`CacheConfig`）：`marketChart`（TTL 15 秒、上限 200 筆，以 symbol＋interval＋limit 為 key）與 `marketTicker`（TTL 3 秒、上限 50 筆，以 symbol 為 key），讓多個瀏覽器分頁同時輪詢時不會等比放大對 Binance 的請求。(`a463f4e`)
- 前端骨架（`frontend/`）：Vite + React 18 + TypeScript，dev server 跑在 5173 並把 `/api` 代理到後端（`VITE_PROXY_TARGET`，預設 `http://localhost:8080`），含深色主題 CSS 變數、已安裝 `lightweight-charts` 與 `vitest`，以及執行 dev server 的 Dockerfile。工具鏈改用 Vite 7／`@vitejs/plugin-react` 5／Vitest 3，而非計畫中的 Vite 5 線——後者相依的 esbuild 有 dev server 安全通報（`npm audit`：0 vulnerabilities）。(`43081c1`)
- 前端行情型別與 API client（`frontend/src/features/market/`）：`types.ts` 對應後端 DTO（`Candle`、`TickerData`、`Indicators`／`MacdSeries`、`ChartData`、`ApiErrorBody`，以及 `INTERVALS` 與 `IntervalCode`），指標序列與 K 棒同長度、同索引，暖機段以 `null` 表示；`api/marketApi.ts` 提供 `fetchTicker()` 與 `fetchChart()`，並把非 2xx 回應的 `ApiError` 轉成帶 `code` 與 `retryAfter` 的 `MarketApiError`，讓輪詢層能據此退避。(`e0e5a2b`)
- 具分頁可見性感知的輪詢 hooks（`frontend/src/features/market/hooks/`）：`usePolling` 立即抓一次後依間隔重抓，**失敗時不清空既有資料**（改標記 `isStale`，因為看盤時空白畫面比舊價格更糟），分頁切到背景就停止輪詢、切回來立刻補抓一次；非 `MarketApiError` 的例外統一包成 `code: 'NETWORK'`。`useTicker` 固定 5 秒輪詢；`useChartData` 依 K 棒週期調整節奏（`CHART_POLL_MS`：1m 20 秒 … 1d 300 秒）。(`3934f25`)
- 前端測試環境：Vitest 改用 `jsdom`，加入 `@testing-library/react` 以測試 hook 行為（`npm audit`：0 vulnerabilities）。(`3934f25`)
- BTC 行情頁（`frontend/src/features/market/`）：`MarketPage` 串起即時報價、週期切換與指標開關；`PriceHeader` 顯示價格／24 小時漲跌與高低量，並在資料延遲或上游被封鎖時顯示對應標記；`IntervalSelector` 提供 1m–1d 六個週期（以 `aria-pressed` 標示選取）；`IndicatorToggles` 可個別開關 SMA200／EMA15-30-45-60／RSI14／MACD；`PriceChart` 以 lightweight-charts v5 的 Panes API 建立單一圖表、四個窗格（K 線＋均線、成交量、RSI 含 30／70 參考線、MACD 含柱狀圖），共用同一條時間軸與十字線。整頁唯讀，不含任何下單路徑。(`e3b42f2`)
- Docker Compose 堆疊（`docker-compose.yml`）：`db`（postgres:16，含 `pg_isready` healthcheck 與 `pgdata` 具名 volume）、`backend`（8080，`HAPPYTRADE_BINANCE_BASE_URL` 由環境變數注入）、`frontend`（5173，`VITE_PROXY_TARGET` 指向 `http://backend:8080`）。`db` 這個切片還沒接上後端，先起起來是為了讓執行中的堆疊對齊 ADR-0001，之後的 AI 訊號切片不必重新配置。(`8254511`)
- `frontend/.dockerignore` 與 `backend/.dockerignore`：前端映像的 `COPY . .` 會把主機（Windows）的 `node_modules` 蓋掉容器裡 `npm ci` 的結果，esbuild／rollup 的原生二進位檔平台不符會讓 dev server 起不來；後端則排除 `target/` 以縮小 build context。(`8254511`)

#### 修正

- 切換到輪詢節奏相同的週期時，圖表不會立即重載：`usePolling` 原本只以輪詢間隔為 effect 相依，而 15m 與 1h 同為 60 秒，導致點下去後畫面最久要等一分鐘才換資料。改為同時相依於 fetcher 本身（`useTicker`／`useChartData` 已用 `useCallback` 記憶化，其識別值恰好在請求內容改變時才變動）。(`e3b42f2`)
- 圖表窗格比例錯誤：原本用掛載當下的 `container.clientHeight` 換算像素高度，但當時 flex 版面尚未定案、讀到的仍是 CSS `min-height`，價格窗格因此過高。改用 `setStretchFactor` 依 60／15／12／13 的比例分配，也能自動適應之後的視窗縮放。(`e3b42f2`)

### 2026-08-17

#### 新增

- 初始專案結構與治理規範。(`b324618`)
- ADR 基礎設定與架構基線。(`b324618`)
