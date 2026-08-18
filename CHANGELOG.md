# 變更記錄（Changelog）

本檔案記錄本專案所有值得注意的變更。

格式參考 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### 新增

- 初始專案結構與治理規範。
- ADR 基礎設定與架構基線。
- 後端 Spring Boot 骨架（`backend/`），含健康檢查冒煙測試、Maven wrapper 與 Dockerfile。此切片不含 JPA／datasource 相依。
- 無外部相依的指標套件：SMA、EMA、RSI（Wilder）與 MACD，訊號線已對回原始 K 棒索引。
- SMA 與 EMA 指標（`Sma`、`Ema`），分別採移動平均與 SMA 起頭的指數平滑，暖機區間皆補 null。
- 行情資料的領域模型：`Candle`（OHLCV record）、`Ticker`（價格與 24 小時統計）、`Interval` enum（時間框架代碼，支援雙向轉換）。
- 無金鑰的 Binance 行情資料提供者（`BinanceMarketDataProvider`），只讀取公開的 klines 與 24hr ticker 端點，並以 `UpstreamException` 對應限流（429/418）、地區封鎖（451）與逾時。不送 API key，也不做請求簽章。
- RSI 平盤處理：完全平盤的價格序列（RS 為 0/0）現在回傳 50（中性），不再誤判為全數上漲。
- 行情圖表服務（`MarketChartService`）：在請求的顯示筆數之上多抓 200 根暖機 K 棒，於完整視窗上計算 SMA200／EMA15-30-45-60／RSI14／MACD，再裁掉暖機段，讓每條指標序列與回傳的 K 棒索引完全對齊。上游回傳筆數少於顯示需求時，改為保留全部 K 棒。
- 唯讀行情 REST 端點：`GET /api/market/ticker` 與 `GET /api/market/chart`（`MarketController`），搭配把三條 MACD 序列收攏成巢狀結構的 `ChartResponse` DTO、symbol／interval／limit 驗證（limit 50-800），以及 `@RestControllerAdvice`：參數錯誤對應 400，上游限流／封鎖／逾時分別對應 503／503／504，回傳結構化的 `ApiError`。所有端點都不下單、也不模擬下單。
- 行情資料的 Caffeine 快取（`CacheConfig`）：`marketChart`（TTL 15 秒、上限 200 筆，以 symbol＋interval＋limit 為 key）與 `marketTicker`（TTL 3 秒、上限 50 筆，以 symbol 為 key），讓多個瀏覽器分頁同時輪詢時不會等比放大對 Binance 的請求。
- 前端骨架（`frontend/`）：Vite + React 18 + TypeScript，dev server 跑在 5173 並把 `/api` 代理到後端（`VITE_PROXY_TARGET`，預設 `http://localhost:8080`），含深色主題 CSS 變數、已安裝 `lightweight-charts` 與 `vitest`，以及執行 dev server 的 Dockerfile。工具鏈改用 Vite 7／`@vitejs/plugin-react` 5／Vitest 3，而非計畫中的 Vite 5 線——後者相依的 esbuild 有 dev server 安全通報（`npm audit`：0 vulnerabilities）。
- 前端行情型別與 API client（`frontend/src/features/market/`）：`types.ts` 對應後端 DTO（`Candle`、`TickerData`、`Indicators`／`MacdSeries`、`ChartData`、`ApiErrorBody`，以及 `INTERVALS` 與 `IntervalCode`），指標序列與 K 棒同長度、同索引，暖機段以 `null` 表示；`api/marketApi.ts` 提供 `fetchTicker()` 與 `fetchChart()`，並把非 2xx 回應的 `ApiError` 轉成帶 `code` 與 `retryAfter` 的 `MarketApiError`，讓輪詢層能據此退避。
- 具分頁可見性感知的輪詢 hooks（`frontend/src/features/market/hooks/`）：`usePolling` 立即抓一次後依間隔重抓，**失敗時不清空既有資料**（改標記 `isStale`，因為看盤時空白畫面比舊價格更糟），分頁切到背景就停止輪詢、切回來立刻補抓一次；非 `MarketApiError` 的例外統一包成 `code: 'NETWORK'`。`useTicker` 固定 5 秒輪詢；`useChartData` 依 K 棒週期調整節奏（`CHART_POLL_MS`：1m 20 秒 … 1d 300 秒）。
- 前端測試環境：Vitest 改用 `jsdom`，加入 `@testing-library/react` 以測試 hook 行為（`npm audit`：0 vulnerabilities）。
