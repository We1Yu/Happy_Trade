# Happy_Trade v1 進度看板

> 唯一的進度真相來源。每完成一個 Task 就在這裡打勾（跟 CHANGELOG 同一次 commit）。
> 分支：`feat/market-page` ｜ 計畫全文：`docs/superpowers/plans/2026-08-18-market-page.md`

## 現在的狀態

**10 / 14 完成（71%）— 後端切片完成，前端骨架與型別／API client 就緒。**

**下一個動作：Task 11 Polling hooks（`usePolling` / `useTicker` / `useChartData`，含錯誤退避）。**

## Task 清單

| # | Task | 狀態 | Commit |
|---|------|------|--------|
| 1 | Backend skeleton + health check | ✅ | `946a69a` |
| 2 | Domain model (Candle / Ticker / Interval) | ✅ | `41a5317` |
| 3 | SMA / EMA 指標 | ✅ | `a759cfe` |
| 4 | RSI / MACD 指標 | ✅ | `c758741` |
| 5 | Binance provider（免金鑰） | ✅ | `9010d6c` |
| 6 | Chart service（warm-up + 對齊） | ✅ | `993d5e2` |
| 7 | REST endpoints + 錯誤對應 | ✅ | `0ea9250` |
| 8 | Caffeine 快取 | ✅ | `a463f4e` |
| 9 | 前端骨架（Vite + React + TS） | ✅ | `43081c1` |
| 10 | 前端 types + API client | ✅ | `a1fb1a7` |
| 11 | Polling hooks（useTicker / useChartData） | ⬜ | — |
| 12 | Market page UI（圖表 + 切換器） | ⬜ | — |
| 13 | Docker Compose（db / backend / frontend） | ⬜ | — |
| 14 | 治理紀錄（ADR 0002 + CHANGELOG 收尾） | ⬜ | — |

圖例：✅ 完成 ｜ 🔄 進行中 ｜ ⬜ 未開始

## 目前能跑什麼

- 後端測試：`cd backend && ./mvnw test`（46 個測試全綠）
- 後端啟動：`cd backend && ./mvnw spring-boot:run` → `localhost:8080`
- 可用 API：`/api/health`、`/api/market/ticker`、`/api/market/chart`
- 前端測試：`cd frontend && npm test`（3 個測試全綠）
- 前端 dev server：`cd frontend && npm run dev` → `localhost:5173`，`/api` 代理到 8080
- 前端建置：`cd frontend && npm run build`（tsc -b + vite build）

## 懸而未決

- 前端畫面（Task 11–12）尚未開始，目前只有型別與 API client
- Docker Compose 還沒寫，目前只能在本機跑後端
- ADR 0002（市場資料與圖表技術選型）尚未寫入
