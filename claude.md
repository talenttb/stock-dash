# Claude 開發指南

此文件記錄專案的開發慣例和架構決策，供 AI agent 參考。

## 開發原則

### 程式碼風格
- 保持精簡，程式碼本身應具備良好的自解釋能力
- 避免不必要的註解，只在邏輯複雜且難以從程式碼直接理解時才添加註解

### 文件更新
- 任何新增或修改功能時，必須同步更新 `README.md`
- README 應包含使用說明和範例

### clj-reload 整合
- 新增模組（如配置、連線池等）時，需要配置 `before-reload` 和 `after-reload` hooks
- 使用 metadata: `^:clj-reload/before-reload` 和 `^:clj-reload/after-reload`
