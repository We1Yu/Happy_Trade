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
- Frontend scaffold (`frontend/`): Vite + React 18 + TypeScript app with a dev server on 5173 proxying `/api` to the backend (`VITE_PROXY_TARGET`, default `http://localhost:8080`), dark theme CSS variables, `lightweight-charts` and `vitest` installed, and a Dockerfile running the dev server. Toolchain pinned to Vite 7 / `@vitejs/plugin-react` 5 / Vitest 3 instead of the planned Vite 5 line, which shipped an esbuild dev-server advisory (`npm audit`: 0 vulnerabilities).
