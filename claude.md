# Claude 開發指南

此文件記錄專案的開發慣例和架構決策，供 AI agent 參考。

## 開發原則
- 文件＆溝通需使用正體中文
- 不要過度設計，我們只需要在結構上適時的留下可以擴充的空間
- **Clojure SDK 設計：對齊原始 SDK**
  - Clojure 應該對齊底層 C/C++ SDK 的設計
  - 避免不必要的抽象層，直接使用 SDK 提供的資料結構
  - 例如：如果 SDK 的函數需要 `Account` 結構，就直接傳遞 account map，不要額外建立 account-id 的抽象
  - **以 personal-id 為核心單位**：login/logout 都以 personal-id 操作，對齊 C++ SDK 的 `login(personal_id) -> vector<Account>` 設計
  - **避免字串拼接作為 key**：使用結構化的 map 和 vector，不用 "personal-id:branch-account" 這種字串作為複合 key

### REPL 開發

本專案使用 `brepl` 進行互動式開發，**優先使用 brepl，避免使用 `clojure -M`**。

#### 何時可以使用 clojure -M？

- **CI/CD 自動化測試**：需要乾淨的環境
- **獨立腳本執行**：不需要互動式開發

### 程式碼風格
- 保持精簡，程式碼本身應具備良好的自解釋能力
- 註解使用80/20法則，只在20％邏輯複雜且難以從程式碼直接理解時才添加註解，大多數的時間不需要註解
- 需要放範例時，使用 comment 在該 fn 後方加上，不需要使用註解做demo；comment更能體現 clojure 的互動性
- 有關網頁相關
  - HTML相關的程式，請使用semantic tag，不要製作太多無意義的div
  - CSS 使用新的 nesting selector，不要使用classname
  - 可以在少數的component加上id，讓 css nesting selector 可以做 compoent style

### 文件更新
- 任何新增或修改功能時，必須同步更新 `README.md`
- README 應包含使用說明和範例

### 狀態管理與 Reload Hooks

有狀態的組件使用 `defonce` + `atom`，並**必須**實作對應的 reload hooks：

| 組件類型 | Reload 行為 | Hooks | 範例 |
|---------|------------|-------|------|
| 無狀態資料 | 重新載入 | `after-reload` 重新載入資料 | `config.clj` |
| 短暫狀態 | 停止並重啟 | `before-reload` 停止 + `after-reload` 重啟 | `logging.clj`, `pathom.clj` |
| 長連線（需登入/WebSocket） | **保持狀態** | 使用 `delay` + `defonce`，不加 hooks | `fubon/lifecycle.clj` |

**重要：新增或修改有狀態的 namespace 時，必須按照上表實作對應的 reload hooks。**

長連線組件避免在 reload 時重啟，以免中斷開發流程（如重新登入）。詳見 `src/stock_dash/fubon/lifecycle.clj`。
