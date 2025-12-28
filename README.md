# stock-dash

A Clojure application for stock dashboard.

## 專案結構

```
stock-dash/
├── deps.edn                    # 依賴管理檔案
├── build.clj                   # 建構腳本
├── scripts/
│   ├── git-hooks/
│   │   └── pre-commit         # Pre-commit hook 腳本
│   └── install-git-hooks.sh   # Git hooks 安裝腳本
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

## 依賴

- **clojure-plus**: 增強開發體驗的工具集
  - 改進的錯誤訊息格式
  - 增強的列印功能
  - 調試用的 `#p` 讀取器巨集
- **cprop**: 配置管理函式庫
  - 支援多種配置來源（環境變數、系統屬性、配置檔案）
  - 自動合併配置層級
- **http-kit**: 輕量高性能 HTTP server/client
  - 基於 Java NIO 的非阻塞 I/O
  - 完全兼容 Ring 規範
  - 支援 WebSocket
- **reitit**: 現代化路由函式庫
  - 資料驅動路由定義
  - 高效能路由匹配
  - 支援中間件和 content negotiation
- **jsonista**: 快速 JSON 處理庫
  - 基於 Jackson
  - 高性能 JSON 序列化/反序列化

## 開發

### Git Hooks 設置

專案提供 pre-commit hook 來防止將調試代碼提交到版本控制。

```bash
# 安裝 git hooks
./scripts/install-git-hooks.sh
```

**Pre-commit 檢查項目：**
- 檢查 `src/` 目錄中是否有 `#p` 調試巨集

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

;; 重新載入配置（同時也會重載程式碼）
(reload)

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
user=> (start)   # 啟動 server
user=> (stop)    # 停止 server
user=> (restart) # 重啟 server
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

**熱重載機制：**

本專案使用 var 引用 (#') 支持熱重載，修改 handler 後無需重啟 server：

```clojure
;; 修改 handler 函數後
user=> (reload)    ; 重載代碼，server 自動使用新的 handler（無需 restart）

;; 只有在以下情況需要 restart：
;; 1. 修改 middleware 配置
;; 2. 修改路由定義
;; 3. 修改 server 配置（port, host 等）
user=> (restart)
```

**性能特性：**

HTTP-Kit 基於 Java NIO，天然支持非阻塞 I/O：
- 每個請求不佔用一個線程
- 高並發處理能力
- 內存佔用低

### 執行程式

```bash
# 直接執行（會啟動 web server）
clj -M:run

# 啟動開發 REPL (包含 clojure-plus 和 clj-reload)
clj -M:dev:repl

# 在 REPL 中重新載入變更的程式碼
user=> (reload)
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