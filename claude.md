# Claude 開發指南

此文件記錄專案的開發慣例和架構決策，供 AI agent 參考。

## 開發原則
- 文件＆溝通需使用正體中文
- 註解使用 **80/20法則**，只在20％邏輯複雜且難以從程式碼直接理解時才添加註解，大多數的時間不需要註解
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

## Schema 驗證（Malli）

使用 Malli 進行 boundary validation，在 SDK 函數入口驗證輸入。

### 使用方式

所有 SDK 公開函數都用 `ensure!` 驗證輸入：

```clojure
(schemas/ensure! :login {your input params})
```

### 新增函數時

1. 在 `sdk-schema` 加上對應的 multi branch [:action :parameters]
2. Schema 要對齊 C SDK 的資料結構
3. 保持簡潔，不過度設計

## C SDK 的 Enum 處理

在 Clojure 使用 **keyword**，FFI 層做 int ↔ keyword 轉換。

### 結構
- `fubon/enums.clj`：集中定義所有 enum 映射和轉換函數
- `ffi.clj`：從 C 讀取時用 `int->xxx`，寫入 C 時用 `xxx->int`
- `sdk.clj`：使用者直接用 keyword
- `schemas.clj`：**enum 值必須定義在 Malli schema 中驗證**

### 範例
```clojure
;; enums.clj
(def order-type-to-int {:stock 1 :margin 2 :short 3 ...})
(def int-to-order-type (clojure.set/map-invert order-type-to-int))
(defn int->order-type [n] (int-to-order-type n n)) ;; fallback 到原始數字

;; schemas.clj - 使用 :enum 定義可用值
[:order-type {:optional true} [:enum :stock :margin :short :sbl :day-trade]]

;; ffi.clj 讀取
{:order-type (enums/int->order-type order-type) ...}

;; sdk.clj 使用
(inventories ...) ;; => [{:order-type :stock ...}]
```

### 主要 Enum
OrderType, MarketType, PriceType, TimeInForce, BSAction, Direction

**按需實作**：實作 API 時才加入對應 enum，並同步更新 Malli schema。
