# Architectural Decision Records (ADR)

新的 ADR 從 `template.md` 複製，檔名格式 `NNNN-short-title.md`（流水號接續現有最大號）。

## 需要寫 ADR

- 資料庫 schema 或持久層架構變更
- 前後端通訊協定變更（例如 REST 改 WebSocket）
- 導入新的外部 API 或第三方套件
- 安全性、認證、風控規則的修改

## 不需要寫 ADR

- 小欄位新增、DTO 微調
- 沒有結構變化的 bug 修復與重構
- 純 UI 樣式或版面調整

## 索引

| # | 標題 | 狀態 |
|---|---|---|
| [0001](0001-initial-system-architecture.md) | Initial System Architecture | accepted |
