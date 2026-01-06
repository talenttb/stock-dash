# stock-dash

A Clojure application for stock dashboard.

## 專案結構

```
stock-dash/
├── deps.edn                    # 依賴管理檔案
├── build.clj                   # 建構腳本
├── resources/
│   └── config.edn             # 配置檔案
├── dev/
│   └── user.clj               # 開發環境設定
├── src/
│   └── stock_dash/
│       ├── core.clj           # 主程式
│       ├── config.clj         # 配置管理
│       ├── server.clj         # Web server 管理
│       └── handler.clj        # 路由和處理器
└── test/
    └── stock_dash/
        └── core_test.clj      # 測試檔案
```

## 開發

### 配置管理

專案使用 cprop 來管理配置。

**配置檔案位置：**
- `resources/config.edn` - 主要配置檔案
- `src/stock_dash/config.clj` - 配置管理模組

**在應用程式中使用配置：**
```clojure
(ns your-namespace
  (:require [stock-dash.config :as config]))

;; 獲取完整配置
(config/get-config)

;; 獲取特定配置項
(get-in (config/get-config) [:server :port])
```

**在開發 REPL 中使用配置：**
```clojure
;; 查看當前配置
(stock-dash.config/get-config)

;; 重新載入配置（同時也會重載程式碼和日誌）
(reset)

;; 只重新載入配置
(stock-dash.config/load-config!)
```

**環境變數覆蓋：**
cprop 支援使用環境變數覆蓋配置，例如：
```bash
export APP__NAME="my-app"  # 對應到 {:app {:name "my-app"}}
```

### Web Server

**啟動 server：**

```bash
# 方式 1: 直接執行（會啟動 web server）
clj -M:run

# 方式 2: 在開發 REPL 中啟動
clj -M:dev:repl

# 在 REPL 中使用便利函數
user=> (start)   # 啟動 server（如果已運行會自動重啟）
user=> (stop)    # 停止 server
user=> (reset)   # 重載代碼、配置和日誌（不重啟 server）
user=> (restart) # 重載代碼並重啟 server（推薦使用）
```

Server 預設會在 `http://localhost:3000` 啟動（可透過 `resources/config.edn` 修改）。

**可用端點：**

- `GET /` - 首頁（HTML）
- `GET /api/health` - 健康檢查（JSON）
- `GET /api/status` - 系統狀態（JSON）


**修改配置：**

編輯 `resources/config.edn` 中的 `:server` 設定：

```clojure
:server {:host "localhost"
         :port 3000}
```

也可以使用環境變數覆蓋：

```bash
export SERVER__PORT=8080
clj -M:run
```

### Logging

專案使用 [mulog](https://github.com/BrunoBonacci/mulog) 作為 logging 框架。

**特色：**
- **Global Context**: 自動在所有 log 中包含 app-name, version, env, host
- **Request Tracing**: 每個 HTTP request 自動生成 trace-id，追蹤整個請求的生命週期
- **Context 繼承**: 所有 nested function 的 logs 自動繼承 request context

**自動記錄的 Request 資訊：**
- `:mulog/trace-id` - 唯一的追蹤 ID
- `:mulog/duration` - 請求處理時間（nanoseconds）
- `:mulog/outcome` - `:ok` 或 `:error`
- `:method`, `:uri`, `:query-string` - HTTP 請求基本資訊
- `:user-agent`, `:referer`, `:remote-addr` - 客戶端資訊

**在程式碼中使用：**
```clojure
(ns your-namespace
  (:require [com.brunobonacci.mulog :as mu]
            [stock-dash.logging :as log]))

;; 記錄一般事件
(mu/log ::event-name :key "value" :count 123)

;; 記錄錯誤（自動添加錯誤資訊）
(log/log-error! ::error-name exception :context "data")

;; 追蹤操作執行時間
(mu/trace ::operation
  [:user-id user-id]
  (do-something))
```

**Log 輸出位置：**
- Console: 開發模式預設啟用（pretty-printed）
- File: `workspace/app.log`（可在 `resources/config.edn` 中配置）

**管理 Publishers：**
```clojure
;; 在 REPL 中重啟 logging
(stock-dash.logging/restart-publishers!)

;; 停止 logging
(stock-dash.logging/stop-publishers!)
```

### 執行測試

```bash
clj -M:test -m clojure.test
```

### 檢查過期的依賴

```bash
# 使用 antq 檢查可更新的依賴
clj -M:outdated
```

## 建構

### 建立 Uberjar

```bash
# 建構獨立可執行的 jar 檔案 (包含 AOT compilation)
clj -T:build uber
```

建構完成後會產生 `target/stock-dash-0.1.0-standalone.jar`

### 執行 Uberjar

```bash
java -jar target/stock-dash-0.1.0-standalone.jar
```

### 清理建構產物

```bash
clj -T:build clean
```

## License

Copyright © 2025