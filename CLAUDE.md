# Happy_Trade

個人量化交易儀表板。

## 專案範圍與安全紅線

- **目標**：個人用量化交易儀表板。
- **範圍**：看盤、AI 訊號、手動／模擬下單。
- **安全紅線**：**不可直接自動下單**（NO direct automatic order execution）。任何試圖直接呼叫券商 API 送單的邏輯都必須被擋下並回報。

## 技術棧

| 層 | 技術 | 位址 |
|---|---|---|
| 前端 | React + TypeScript + Vite | `localhost:5173` |
| 後端 | Java 21 + Spring Boot | `localhost:8080` |
| 資料庫 | PostgreSQL | `localhost:5432` |
| 容器 | Docker Compose | services: `db`, `backend`, `frontend` |

## 對話開場協議

每個新對話的第一個任務回覆，都必須先輸出這個摘要區塊，再開始做事：

```
[Project Context Restatement]
- Project: Happy_Trade (Quant Dashboard) | Scope: Charting, AI Signal, Sim/Manual Trade (NO Auto-Order)
- Tech: React (5173), Spring Boot (8080), Postgres (5432), Docker Compose
- Current Assumptions & Task: <關鍵假設與這次的範圍>
```

## 寫程式的方式（學習導向）

- **先講意圖與取捨**：給程式碼之前，先用一小段說明為什麼這樣做。
- **小步走**：複雜改動拆成可逐步驗證的步驟。
- **每步都可驗證**：附上驗證用的指令或測試。
- **不要寫教科書**：說明簡潔直接。

## 語言政策

- Sub-agent 的內部推理、prompt 檔案、程式碼、註解、commit message → **英文**。
- 對使用者的所有對話回覆 → **繁體中文**。

## 治理與紀錄

- Sub-agent 規範：`.claude/agents/happy-trade-governance.md`
- 架構決策：符合條件的變更必須寫入 `docs/adr/`（判斷標準見 `docs/adr/README.md`）。
- 實作變更：必須更新 `CHANGELOG.md` 的 `[Unreleased]` 區塊。
