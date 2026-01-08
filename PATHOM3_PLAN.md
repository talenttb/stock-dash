# Pathom3 集成實現計畫

## 概述

為 stock-dash 專案集成 Pathom3，實現從 handler 到 DAG 的完整資料流，使用 chassis 渲染 HTML fragments。**本階段使用模擬資料，專注於 DAG 建置和 HTML 渲染機制**。

## 需求總結

### 核心功能
- **Pathom3 DAG**: 從 handler 呼叫開始接入依賴圖
- **Pathom3 DAG**: 從 REPL 執行 EQL process 驗證實作邏輯
- **HTML 渲染**: 使用 chassis 生成 HTML fragments 作為 resolver 輸出
- **模擬資料**: 使用簡單的 mock 函數提供測試資料
- **並行執行**: Pathom 自動並行建置 DAG
- **錯誤處理**: 局部降級（失敗的 resolver 回傳預設值）

### 技術決策
- HTML 模板: `dev.onionpancakes/chassis`
- 資料來源: 模擬函數（hardcoded data）
- 狀態管理: defonce + atom（遵循 CLAUDE.md）
- 程式碼組織: **單一檔案** `pathom.clj`
- View 粒度: 混合模式（大區塊 + 小元件）

### 資料來源
- 模擬股票資料（hardcoded maps）
- 模擬計算邏輯
- 靜態配置資料

## 實現步驟

### 步驟 1: 添加依賴

**檔案**: `deps.edn`

添加依賴：
```clojure
;; Pathom3
com.wsscode/pathom3 {:mvn/version "2024.08.22-alpha"}

;; Chassis HTML 模板
dev.onionpancakes/chassis {:mvn/version "1.0.514"}
```

**驗證**: `clj -M:repl` 啟動無錯誤

---

### 步驟 2: 實現 Pathom 核心

**檔案**: `src/stock_dash/pathom.clj`（新建，單一檔案）

**架構分層**:

#### 2.1 模擬資料函數
```clojure
(defn mock-stocks-list []
  "回傳模擬的股票列表"
  [{:stock/id 1
    :stock/symbol "AAPL"
    :stock/name "Apple Inc."
    :stock/opening-price 150.0}
   {:stock/id 2
    :stock/symbol "GOOGL"
    :stock/name "Alphabet Inc."
    :stock/opening-price 2800.0}
   {:stock/id 3
    :stock/symbol "TSLA"
    :stock/name "Tesla Inc."
    :stock/opening-price 700.0}])

(defn mock-current-price [stock-id]
  "模擬當前價格（加入隨機波動）"
  (let [base-prices {1 152.5, 2 2850.0, 3 720.0}]
    (get base-prices stock-id 100.0)))

(defn mock-shares-outstanding [stock-id]
  "模擬流通股數"
  (let [shares {1 16e9, 2 13e9, 3 1e9}]
    (get shares stock-id 1e9)))
```

#### 2.2 資料 Resolvers
```clojure
(pco/defresolver stocks-list-resolver [env _]
  "取得所有股票列表（使用模擬資料）"
  {::pco/output [{:stocks [:stock/id :stock/symbol :stock/name :stock/opening-price]}]}
  {:stocks (mock-stocks-list)})

(pco/defresolver stock-current-price-resolver [env {:stock/keys [id]}]
  "取得股票當前價格（使用模擬資料）"
  {::pco/output [:stock/current-price]}
  {:stock/current-price (mock-current-price id)})

(pco/defresolver stock-shares-resolver [env {:stock/keys [id]}]
  "取得股票流通股數（使用模擬資料）"
  {::pco/output [:stock/shares-outstanding]}
  {:stock/shares-outstanding (mock-shares-outstanding id)})
```

#### 2.3 計算 Resolvers
```clojure
(pco/defresolver price-change-resolver
  [env {:stock/keys [current-price opening-price]}]
  "計算價格變化百分比"
  {::pco/output [:stock/price-change-pct]}
  (let [change (- current-price opening-price)
        pct (* 100 (/ change opening-price))]
    {:stock/price-change-pct pct}))

(pco/defresolver market-cap-resolver
  [env {:stock/keys [current-price shares-outstanding]}]
  "計算市值"
  {::pco/output [:stock/market-cap]}
  {:stock/market-cap (* current-price shares-outstanding)})
```

#### 2.4 View Resolvers（Chassis 整合）
```clojure
(require '[dev.onionpancakes.chassis.core :as c])

;; HTML 輔助函數
(defn format-price [price]
  "格式化價格顯示"
  (format "%.2f" price))

(defn format-pct [pct]
  "格式化百分比顯示"
  (format "%+.2f%%" pct))

(defn price-color [change-pct]
  "根據漲跌回傳顏色"
  (cond
    (> change-pct 0) "green"
    (< change-pct 0) "red"
    :else "gray"))

;; 小元件 - 股票卡片
(pco/defresolver stock-card-html-resolver
  [env {:stock/keys [symbol name current-price price-change-pct]}]
  "渲染單個股票卡片 HTML fragment"
  {::pco/output [:stock/card-html]}
  (let [color (price-color price-change-pct)
        html-str (c/html
                  [:div {:class "stock-card"}
                   [:h3 symbol]
                   [:p name]
                   [:div {:class "price"}
                    [:span (format-price current-price)]
                    [:span {:style (str "color:" color)}
                     (format-pct price-change-pct)]]])]
    {:stock/card-html html-str}))

;; 大區塊 - 股票列表
(pco/defresolver stocks-list-html-resolver [env {:keys [stocks]}]
  "渲染股票列表容器 HTML"
  {::pco/output [:view/stocks-list-html]}
  (let [cards-html (map :stock/card-html stocks)
        html-str (c/html
                  [:div {:class "stocks-list"}
                   cards-html])]
    {:view/stocks-list-html html-str}))

;; Header
(pco/defresolver header-html-resolver [env input]
  "渲染頁面 header HTML fragment"
  {::pco/output [:view/header-html]}
  (let [title (get input :page-title "Stock Dashboard")
        html-str (c/html
                  [:header
                   [:h1 title]
                   [:p "即時股票監控儀表板"]])]
    {:view/header-html html-str}))

;; 完整頁面
(pco/defresolver page-html-resolver
  [env {:view/keys [header-html stocks-list-html]}]
  "組合完整頁面 HTML"
  {::pco/output [:view/page-html]}
  (let [html-str (c/html
                  [:html
                   [:head
                    [:meta {:charset "UTF-8"}]
                    [:title "Stock Dashboard"]
                    [:style "
                      body { font-family: Arial, sans-serif; margin: 20px; }
                      .stock-card { border: 1px solid #ddd; padding: 15px; margin: 10px; border-radius: 5px; }
                      .price { font-size: 1.2em; font-weight: bold; }
                    "]]
                   [:body
                    header-html
                    stocks-list-html]])]
    {:view/page-html html-str}))
```

#### 2.5 註冊和狀態管理
```clojure
(def all-resolvers
  [stocks-list-resolver
   stock-current-price-resolver
   stock-shares-resolver
   price-change-resolver
   market-cap-resolver
   stock-card-html-resolver
   stocks-list-html-resolver
   header-html-resolver
   page-html-resolver])

(defonce registry (atom nil))

(defn start-pathom! []
  "初始化 Pathom registry"
  (reset! registry (pci/register all-resolvers))
  (println "✓ Pathom registry initialized"))

(defn stop-pathom! []
  "停止 Pathom，清空 registry"
  (reset! registry nil)
  (println "✓ Pathom registry cleared"))

(defn get-registry []
  @registry)

;; Reload hooks (遵循 CLAUDE.md)
(defn ^:clj-reload/before-reload before-reload []
  (println "Reloading pathom namespace"))

(defn ^:clj-reload/after-reload after-reload []
  (println "✓ Pathom reloaded"))
```

#### 2.6 EQL 處理器
```clojure
(defn process-eql
  "執行 EQL 查詢"
  [eql-query]
  (if-let [reg @registry]
    (let [result (p.eql/process reg eql-query)]
      result)
    (throw (ex-info "Pathom registry not initialized. Call start-pathom! first." {}))))
```

**驗證**:
```clojure
(pathom/start-pathom!)
(pathom/process-eql [{:stocks [:stock/symbol :stock/name :stock/current-price]}])
```

---

### 步驟 3: 整合 Handler

**檔案**: `src/stock_dash/handler.clj`

**修改內容**:

1. **新增 require**
```clojure
[stock-dash.pathom :as pathom]
```

2. **建立新 handler**
```clojure
(defn stock-dashboard
  "股票儀表板頁面"
  [request]
  (let [eql-query [{:stocks [:stock/id
                             :stock/symbol
                             :stock/name
                             :stock/opening-price
                             :stock/current-price
                             :stock/price-change-pct
                             :stock/card-html]}
                   :view/header-html
                   :view/stocks-list-html
                   :view/page-html]
        result (pathom/process-eql eql-query)
        html (:view/page-html result)]
    {:status 200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body html}))
```

3. **新增路由**
```clojure
(def routes
  [["/" {:handler #'home-handler}]
   ["/stocks" {:handler #'stock-dashboard}]  ;; 新增
   ["/api"
    ["/health" {:handler #'health-handler}]
    ["/status" {:handler #'status-handler}]]])
```

**驗證**:
```bash
curl http://localhost:8080/stocks
# 應回傳完整 HTML 頁面
```

---

### 步驟 4: 更新 REPL 工具

**檔案**: `dev/user.clj`

**新增內容**:

1. **新增 require**
```clojure
[stock-dash.pathom :as pathom]
```

2. **增強 reset 函數**
```clojure
(defn reset
  "重載代碼、配置、日誌和 Pathom"
  []
  (reload/reload)
  (config/load-config!)
  (log/restart-publishers!)
  (pathom/stop-pathom!)
  (pathom/start-pathom!)
  (println "✓ Code, config, logging, and pathom reloaded"))
```

3. **Pathom 除錯工具**
```clojure
(defn test-eql [query]
  "在 REPL 中測試 EQL 查詢"
  (pathom/process-eql query))

(defn show-resolvers []
  "顯示所有已註冊的 resolvers"
  (keys (:com.wsscode.pathom3.connect.indexes/index-resolvers
         (pathom/get-registry))))
```

4. **範例用法 comment**
```clojure
(comment
  (start)

  ;; 測試簡單查詢
  (test-eql [{:stocks [:stock/symbol :stock/name]}])

  ;; 測試完整查詢（包含 HTML）
  (test-eql [{:stocks [:stock/symbol
                       :stock/name
                       :stock/current-price
                       :stock/price-change-pct
                       :stock/card-html]}
             :view/header-html
             :view/stocks-list-html
             :view/page-html])

  ;; 查看所有 resolvers
  (show-resolvers)

  ;; 完整重啟
  (restart)
  ;;
  )
```

**驗證**: 在 REPL 中測試所有新函數

---

### 步驟 5: 更新啟動流程

**檔案**: `src/stock_dash/core.clj`

**修改 -main 函數**:
```clojure
(defn -main [& args]
  (log/start-publishers!)
  (pathom/start-pathom!)   ;; 新增
  (server/start-server! #'handler/app)
  (mu/log ::application-started))
```

**驗證**: `clj -M:run` 啟動無錯誤

---

### 步驟 6: 更新 README

**檔案**: `README.md`

**新增章節**:

1. **Pathom3 整合說明**
2. **新增 API 端點**: `/stocks` - 股票儀表板頁面
3. **REPL 開發工具**: Pathom 測試函數
4. **技術棧**: Pathom3 + Chassis HTML

---

## 關鍵檔案清單

### 需要修改的檔案
1. `/Users/liyu/talenttb/stock-dash/deps.edn` - 添加 Pathom3 和 Chassis 依賴
2. `/Users/liyu/talenttb/stock-dash/src/stock_dash/handler.clj` - 整合 Pathom handler
3. `/Users/liyu/talenttb/stock-dash/src/stock_dash/core.clj` - 更新啟動流程
4. `/Users/liyu/talenttb/stock-dash/dev/user.clj` - 添加 REPL 工具
5. `/Users/liyu/talenttb/stock-dash/README.md` - 更新文件

### 需要建立的檔案
1. `/Users/liyu/talenttb/stock-dash/src/stock_dash/pathom.clj` - Pathom 核心（單一檔案）

---

## 資料流示意

```
HTTP Request (/stocks)
    ↓
Handler (stock-dashboard)
    ↓
EQL Query 定義
    ↓
Pathom (process-eql)
    ↓
DAG 建置 & 並行執行
    ↓
Resolvers 執行:
    ├── Data (Mock)   → 提供模擬資料
    ├── Calc (計算)    → 依賴資料完成後執行
    └── View (HTML)    → 依賴資料和計算完成後渲染
    ↓
Result 組裝
    ↓
完整 HTML 頁面回傳
```

---

## 範例 EQL 查詢

```clojure
;; 完整儀表板查詢
[{:stocks [:stock/id
           :stock/symbol
           :stock/name
           :stock/opening-price
           :stock/current-price
           :stock/price-change-pct
           :stock/card-html]}
 :view/header-html
 :view/stocks-list-html
 :view/page-html]
```

**執行流程**:
1. `:stocks` → `stocks-list-resolver` (模擬資料)
2. 對每個 stock:
   - `:current-price` → `stock-current-price-resolver` (模擬資料)
   - `:price-change-pct` → `price-change-resolver` (計算)
   - `:card-html` → `stock-card-html-resolver` (Chassis HTML)
3. `:view/stocks-list-html` → 組合所有 card-html
4. `:view/header-html` → 渲染 header
5. `:view/page-html` → 組合完整頁面

---

## 注意事項

### 架構一致性
- 所有狀態管理使用 `defonce + atom` 模式（遵循 CLAUDE.md）
- 針對每個 namespace 提供 `:clj-reload/before-reload` 和 `:clj-reload/after-reload` hooks
- reload 時不自動停止元件，由開發者在 REPL 中手動控制
- 整合到現有的 `reset`/`restart` REPL 工作流

### DAG 建置重點
- Pathom3 會自動分析 resolver 之間的依賴關係建立 DAG
- 獨立的 resolvers 會自動並行執行
- 透過 `::pco/output` 聲明 resolver 產出，Pathom 據此建置依賴圖
- 透過 resolver 參數聲明依賴的資料

### 開發工作流
1. 啟動：`(start)`
2. 修改程式碼後：`(reset)` 熱重載
3. 在 REPL 測試查詢：`(test-eql [...])`
4. 查看所有 resolvers：`(show-resolvers)`
5. 完整重啟：`(restart)`

---

## 驗證清單

- [ ] 依賴添加完成（Pathom3 + Chassis），REPL 啟動無錯誤
- [ ] pathom.clj 實現，所有 resolvers 註冊成功
- [ ] 在 REPL 中可以執行 `(test-eql [...])` 並看到結果
- [ ] handler.clj 整合，/stocks 路由回傳 HTML
- [ ] user.clj 工具函數可用（test-eql, show-resolvers）
- [ ] 瀏覽器訪問 http://localhost:8080/stocks 顯示儀表板
- [ ] HTML 正確渲染股票卡片（包含價格、漲跌幅）
- [ ] README 文件更新完整

---

## 後續擴展建議

1. **接入真實資料庫**: 使用 HikariCP + next.jdbc + HoneySQL
2. **真實 API 整合**: 連接實際股票資料 API（如 Alpha Vantage）
3. **添加快取**: 使用 core.cache 為 resolver 添加快取機制
4. **錯誤處理**: 實現局部降級機制
5. **測試**: 添加 resolver 單元測試和整合測試

---

## 總結

本計畫提供了**簡化版** Pathom3 整合方案，專注於核心機制：
- 聲明式資料需求（EQL）
- 自動依賴解析和並行執行（DAG）
- HTML fragment 渲染（Chassis）
- 模擬資料來源（易於測試和理解）
- 完整的 REPL 開發工具支援

所有設計決策遵循專案現有架構模式，保持程式碼組織在單一 `pathom.clj` 檔案中，並完全融入現有的開發工作流。

**本階段目標**: 建立 Pathom3 DAG 機制，驗證 HTML fragment 渲染流程，為後續接入真實資料源打下基礎。
