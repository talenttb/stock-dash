# Claude 開發指南

此文件記錄專案的開發慣例和架構決策，供 AI agent 參考。

## 開發原則
- 文件＆溝通需使用正體中文

### 程式碼風格
- 保持精簡，程式碼本身應具備良好的自解釋能力
- 避免不必要的註解，只在邏輯複雜且難以從程式碼直接理解時才添加註解
- 有關網頁相關
  - HTML相關的程式，請使用semantic tag，不要製作太多無意義的div
  - CSS 使用新的 nesting selector，不要使用classname
  - 可以在少數的component加上id，讓 css nesting selector 可以做 compoent style

### 文件更新
- 任何新增或修改功能時，必須同步更新 `README.md`
- README 應包含使用說明和範例

### 狀態管理
- 有狀態的組件使用 `defonce` + `atom` 模式
- 針對每個 namespace 提供 `:clj-reload` hooks，在 reload 時自動重啟狀態
- 各 namespace 負責自己的生命週期管理

範例：
```clojure
;; 有狀態組件（如 logging, pathom）
(defn ^:clj-reload/before-reload before-reload []
  (println "Reloading...")
  (stop-component!))  ; 在 reload 前停止

(defn ^:clj-reload/after-reload after-reload []
  (start-component!)  ; reload 後自動重啟
  (println "✓ Reloaded"))

;; 無狀態組件（如 config）
(defn ^:clj-reload/after-reload after-reload []
  (load-config!)      ; 只需重新載入資料
  (println "✓ Configuration reloaded"))
```
