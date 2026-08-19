# CI 測試閘門與分支保護設計

* 日期：2026-08-18
* 狀態：accepted（三層皆已實作並驗證，見 §9）
* 範圍：在既有 GitHub remote（`We1Yu/Happy_Trade`）上建立自動化測試閘門，讓未通過測試的變更無法進入 `main`。

## 1. 目標

把「測試必須通過」從人的自律變成流程強制。具體要達成三層防線：

1. **本機 push 前**：跑完整測試，紅燈就擋住 push。
2. **GitHub PR 上**：CI 重跑同一組測試，結果公開可見。
3. **merge 到 `main` 前**：分支保護規則要求 CI 綠燈，且禁止直接 push 到 `main`。

### 成功條件

1. 在 backend 或 frontend 故意寫一個失敗測試後，`git push` 被本機 hook 擋下。
2. 同一個失敗測試若用 `--no-verify` 繞過推上去，PR 頁面顯示 CI 紅燈。
3. CI 紅燈時，GitHub 的 Merge 按鈕被鎖住。
4. 直接對 `main` 執行 `git push` 被拒絕。
5. 測試修好後，CI 轉綠、PR 可 merge。

## 2. 為什麼不擋在 commit

GitHub Actions 只有在 push 之後才會執行，**沒有任何 CI 服務有能力阻止本機的 commit**。唯一能擋 commit 的是 `pre-commit` git hook。

本設計刻意不擋 commit，而是擋 push，理由是：

* 後端 Maven 測試每次要跑數十秒。掛在 `pre-commit` 上會讓「小步走、每步可驗證」的開發節奏變成每次 commit 都要等。
* commit 是本機的、可修改的（rebase、amend）；push 才是不可逆地把東西送出去。閘門放在不可逆的那一步，成本效益最好。

## 3. 現況前提

* Remote 已存在：`https://github.com/We1Yu/Happy_Trade.git`，`main` 已在 origin 上。
* 尚無 `.github/` 目錄、無任何 git hook。
* 後端 `pom.xml` 目前**沒有** JPA 或 Postgres 相依，測試不需要資料庫。CI 因此不需要 service container。
* 後端 Java 21、Spring Boot 3.3.5，以 Maven Wrapper（`./mvnw`）建置。
* 前端 Node + Vite 7 + Vitest 3，`npm run build` 內含 `tsc -b` 型別檢查。

## 4. 元件

### 4.1 `.github/workflows/ci.yml`

觸發條件：

* `push` 到 `main`
* `pull_request` 目標為 `main`

兩個互相獨立、平行執行的 job：

| Job | 步驟 |
|---|---|
| `backend` | checkout → `setup-java` (temurin 21, `cache: maven`) → `./mvnw -B verify` |
| `frontend` | checkout → `setup-node` (Node 20, `cache: npm`, `cache-dependency-path: frontend/package-lock.json`) → `npm ci` → `npm run build` → `npm test` |

兩個 job 名稱即是分支保護要求的 status check 名稱，必須保持穩定。

前端 job 的 `working-directory` 統一設為 `frontend`。

`npm run build` 放在 `npm test` 之前，讓型別錯誤能比測試失敗更早、以更明確的訊息暴露出來。

### 4.2 `.githooks/pre-push`

進版控的 hook 目錄，透過 `git config core.hooksPath .githooks` 啟用（此設定為本機設定，clone 後需執行一次）。

行為：

* 依序執行後端與前端測試，任一失敗即以非零狀態結束，push 被中止。
* 只在對應目錄存在時才執行該段，避免在不完整的 checkout 中誤報。
* 輸出繁體中文的失敗說明，並提示緊急繞道方式 `git push --no-verify`。

腳本以 POSIX `sh` 撰寫，在 Windows 上由 Git for Windows 附帶的 sh 執行。

由於開發環境為 Windows，需在 `.gitattributes` 加上 `.githooks/** text eol=lf`，避免 CRLF 換行讓 shebang 失效（典型症狀是 `bad interpreter`）。

### 4.3 `main` 分支保護

以 `gh api` 設定，要求：

* 必須通過 `backend` 與 `frontend` 兩個 status check。
* 分支必須與 `main` 保持最新（strict）。
* 禁止 force push 與刪除分支。

**已知風險**：若此 repository 為 private，GitHub 免費方案不提供 branch protection。屆時的處理順序是：先嘗試 repository ruleset API；若同樣受限，則向使用者回報，由使用者決定是否將 repo 轉為 public，或接受只有前兩層防線。此為需使用者決策的事項，不自行改動 repo 可見性。

## 5. 開發流程的變化

```
feat/xxx 分支
  → 本機 commit（不受阻擋）
  → git push（pre-push hook 跑測試，紅燈擋下）
  → 開 PR 指向 main
  → GitHub Actions 跑 backend + frontend
  → 兩個都綠燈才能 merge
```

`main` 不再接受直接 push。現行的 `feat/market-page` 分支未來必須經由 PR 併入。

## 6. 錯誤處理

| 情境 | 行為 |
|---|---|
| 本機測試失敗 | pre-push 中止 push，印出失敗的是 backend 還是 frontend |
| 本機缺少 Node 或 JDK | pre-push 視為失敗並提示缺少的工具，不靜默略過 |
| CI 失敗 | PR 顯示紅燈，Merge 按鈕鎖住 |
| 需要緊急 push | `git push --no-verify` 可繞過本機 hook，但**繞不過 CI 與分支保護** |

## 7. 驗證方式

實作完成後，以第 1 節的五個成功條件逐項實測，用一個臨時的失敗測試觸發，驗證完即還原。

## 8. 不在本次範圍

* CD（自動部署）。目前尚無部署目標，Docker Compose 骨架也還沒建立（屬於後續 Task）。本次只做 CI 與閘門。
* 測試覆蓋率門檻、靜態分析（linter、SpotBugs）。可日後另行加入 job。
* Dependabot 或安全掃描。

## 9. 實作結果（2026-08-19 補記）

三層防線全部實作完成，第 1 節的五個成功條件逐項實測通過。

### 實作內容

| 層 | 檔案／設定 | 狀態 |
|---|---|---|
| 1. 本機 push 前 | `.githooks/pre-push`（需 `git config core.hooksPath .githooks`） | ✅ |
| 2. GitHub CI | `.github/workflows/ci.yml`，`backend` / `frontend` 兩個平行 job | ✅ |
| 3. 分支保護 | `main`：要求 2 個 status check（strict）、必須經 PR、`enforce_admins`、禁止 force push 與刪除 | ✅ |

### 成功條件驗證

1. ✅ 前端塞入故意失敗的測試 → hook 以非零狀態中止 push，印出「前端測試失敗（frontend）」，驗證後移除。
2. ✅ 以 `--no-verify` 繞過本機 hook 直推 → 被伺服器端擋下（見第 4 項）。
3. ✅ 分支保護要求 `backend`、`frontend` 兩個 check，未通過即無法 merge。
4. ✅ 直接對 `main` push 被拒：`GH006: Protected branch update failed` / `Changes must be made through a pull request` / `2 of 2 required status checks are expected`。
5. ✅ CI 兩個 job 皆綠（`backend` 33 秒、`frontend` 30 秒），PR 可 merge。

### 與規格的差異與意外

* **repo 已由 private 轉為 public**（使用者決定）。免費方案的 private repo 對 branch protection 與 rulesets 一律回 403；轉 public 後解鎖，Actions 額度也由每月 2,000 分鐘變為無限。轉換前已掃過追蹤中的檔案，無 `.env`、無交易所金鑰（`BinanceMarketDataProviderTest` 中唯一的相關字串是「斷言不送出 API key」的測試），僅 `docker-compose.yml` 帶一個本機用的 `POSTGRES_PASSWORD` 預設值。
* `backend/mvnw` 在 git index 是 `100644`，Linux runner 上會因缺少執行位元而失敗，已以 `git update-index --chmod=+x` 修正為 `100755`。
* **首次 CI 紅燈，暴露出本機與 CI 的環境落差**：規格寫的 Node 20 不在 `jsdom@30`（`^22.22.2 || ^24.15.0 || >=26`）與 `undici@8.10`（`>=22.19.0`）的支援範圍內，前端測試在載入階段就死於 `TypeError: webidl.util.markAsUncloneable is not a function`，9 個測試檔一個都沒跑到。本機 Node v24.15.0 完全看不出這個問題。CI 改用 Node 24，並把 `engines.node` 寫進 `frontend/package.json`、以 `.npmrc` 的 `engine-strict=true` 強制檢查。
* CI 的 action 一併升版（`checkout@v7`、`setup-node@v7`、`setup-java@v5`），清掉 runner 的 Node 20 執行環境淘汰警告。
* §8 所述「Docker Compose 骨架也還沒建立」在本文件撰寫後已完成（Task 13，`8254511`）；CI 目前仍不含容器建置。

### 緊急繞道

`enforce_admins` 為開啟狀態，倉庫擁有者同樣不能直接推 `main`。真的需要繞過時，先關掉該設定再推、推完立刻補回：

```
gh api -X DELETE repos/We1Yu/Happy_Trade/branches/main/protection/enforce_admins
gh api -X POST   repos/We1Yu/Happy_Trade/branches/main/protection/enforce_admins
```
