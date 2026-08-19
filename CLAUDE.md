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

- Sub-agent 的內部推理、prompt 檔案（含 `docs/superpowers/plans/` 的實作計畫）、程式碼、註解、commit message → **英文**。
- 對使用者的所有對話回覆 → **繁體中文**。
- **文件一律繁體中文**：`CHANGELOG.md`、`docs/adr/`、`docs/superpowers/specs/`。新寫與改寫都用中文，不要退回英文。
  - 保持英文原文的部分：程式碼區塊、識別字（類別／方法／欄位名）、檔案路徑、API 路徑、HTTP 方法、套件與工具名稱、ADR 的 `Status` 值（`proposed` / `accepted` / …）、ADR 檔名 slug。
  - CHANGELOG 區塊標題用中文：`### 新增`、`### 變更`、`### 修正`、`### 移除`。

## 治理與紀錄

- Sub-agent 規範：`.claude/agents/happy-trade-governance.md`
- 架構決策：符合條件的變更必須寫入 `docs/adr/`（判斷標準見 `docs/adr/README.md`）。
- 實作變更：必須更新 `CHANGELOG.md` 的 `[Unreleased]` 區塊，格式如下：
  - **依日期分組**：`[Unreleased]` 底下先開 `### YYYY-MM-DD`（新的日期在上），日期底下再分 `#### 新增` / `#### 變更` / `#### 修正` / `#### 移除`。同一天已有區塊就往裡面加，不要新開一個。
  - **每條結尾附 commit hash**，寫成 (`abc1234`)。同一次 commit 帶進多條變更時，每條都附同一個 hash。
  - **日期用該變更 commit 的日期**（`git log --date=short` 的 `%ad`），不是寫文件當天。
  - **hash 在後續 docs commit 補上**：功能 commit 先寫條目、hash 位置留 `(TBD)`，緊接著的 `docs:` commit 一併補 `CHANGELOG.md` 與 `docs/PROGRESS.md` 的 hash。
- 進度看板：每完成一個 Task，必須在同一次 commit 更新 `docs/PROGRESS.md`（打勾、填 commit hash、改寫「現在的狀態」與「下一個動作」）。
