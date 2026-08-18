# Happy_Trade v1 進度看板

> 唯一的進度真相來源。每完成一個 Task 就在這裡打勾（跟 CHANGELOG 同一次 commit）。
> 分支：`feat/market-page` ｜ 計畫全文：`docs/superpowers/plans/2026-08-18-market-page.md`

## 現在的狀態

**12 / 14 完成（86%）— 行情頁可以看了，剩下容器化與治理收尾。**

**下一個動作：Task 13 Docker Compose（db / backend / frontend 三個服務）。**

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
| 10 | 前端 types + API client | ✅ | `e0e5a2b` |
| 11 | Polling hooks（useTicker / useChartData） | ✅ | `3934f25` |
| 12 | Market page UI（圖表 + 切換器） | ✅ | `35addbe` |
| 13 | Docker Compose（db / backend / frontend） | ⬜ | — |
| 14 | 治理紀錄（ADR 0002 + CHANGELOG 收尾） | ⬜ | — |

圖例：✅ 完成 ｜ 🔄 進行中 ｜ ⬜ 未開始

## 目前能跑什麼

- 後端測試：`cd backend && ./mvnw test`（46 個測試全綠）
- 後端啟動：`cd backend && ./mvnw package -DskipTests` 後 `java -jar target/happy-trade-backend-0.1.0-SNAPSHOT.jar` → `localhost:8080`
- 可用 API：`/api/market/ticker`、`/api/market/chart`
- 行情頁：後端＋前端都跑起來後開 `localhost:5173`，四窗格圖表已用真實 Binance 資料驗過
- 前端測試：`cd frontend && npm test`（35 個測試全綠）
- 前端 dev server：`cd frontend && npm run dev` → `localhost:5173`，`/api` 代理到 8080
- 前端建置：`cd frontend && npm run build`（tsc -b + vite build）

## 懸而未決

- Docker Compose 還沒寫，目前只能在本機跑後端
- ADR 0002（市場資料與圖表技術選型）尚未寫入
- `./mvnw spring-boot:run` 在本機跑不起來：專案路徑含非 ASCII 字元（`文件`），Maven fork 出去的 JVM 收到被編碼破壞的 classpath，會噴 `ClassNotFoundException: com.happytrade.HappyTradeApplication`。改用打包後 `java -jar` 可正常啟動
- Task 1 名為「health check」，但後端其實沒有 `/api/health` 這個 HTTP 端點（只有 context 載入的冒煙測試）；先前看板誤記為可用
