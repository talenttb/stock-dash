# stock-dash

A Clojure application for stock dashboard with Fubon Securities SDK integration using Panama FFI.

## 快速開始

```bash
# 1. Clone 專案並初始化 submodule
git clone <repository-url>
cd stock-dash
git submodule update --init --recursive

# 2. 編譯 native library 和 Java bindings
bash scripts/build-native.sh
bash scripts/generate-bindings.sh
clj -T:build compile-java

# 3. 啟動應用程式
clj -M:run
```

應用程式預設會在 `http://localhost:3000` 啟動。

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
│       ├── handler.clj        # 路由和處理器
│       └── fubon/             # 富邦證券 SDK 整合
│           ├── lifecycle.clj  # SDK 生命週期管理
│           ├── ffi.clj        # 低階 FFI bindings
│           └── sdk.clj        # 高階 Clojure API
├── native/                     # Native library 相關
│   ├── fubon-sdk-ffi/         # Git submodule (C wrapper)
│   ├── generated/             # jextract 生成的 Java bindings
│   └── lib/                   # 編譯後的動態函式庫 (.dylib)
├── scripts/
│   ├── build-native.sh        # 編譯 C wrapper
│   └── generate-bindings.sh   # 生成 Java bindings
└── test/
    └── stock_dash/
        └── core_test.clj      # 測試檔案
```

## 環境需求

### 基本需求
- **Java 25** (Temurin 25.0.1 或更新版本) - Panama FFI 正式版
- **Clojure CLI** - 用於執行和建構專案
- **jextract 25** - 生成 Java FFI bindings
- **CMake** - 編譯 C wrapper
- **C++ 編譯器** - 編譯 native library

### 安裝 jextract

1. 下載 jextract 25：
   ```bash
   # 從 https://jdk.java.net/jextract/ 下載
   # 解壓到 /opt/jextract-25
   ```

2. 設定環境變數（加入 `~/.zshrc` 或 `~/.bashrc`）：
   ```bash
   export JEXTRACT_HOME=/opt/jextract-25
   ```

3. 驗證安裝：
   ```bash
   $JEXTRACT_HOME/bin/jextract --version
   # 應顯示：jextract 25
   ```

## 開發

### 初次設定

首次 clone 專案後，需要初始化並編譯 native library：

```bash
# 0. 檢查環境（推薦）
bash scripts/check-env.sh

# 1. 初始化 git submodule
git submodule update --init --recursive

# 2. 編譯 native library
bash scripts/build-native.sh

# 3. 生成 Java bindings
bash scripts/generate-bindings.sh

# 4. 編譯 Java classes
clj -T:build compile-java
```

**環境檢查腳本：** `scripts/check-env.sh` 會自動檢查所有環境需求，並提示缺少的項目。

### Fubon SDK 整合

專案整合了富邦證券 SDK，使用 **Panama FFI** (Java 25) 進行 C library 綁定。

**架構：**
- **C Library** (`native/fubon-sdk-ffi`) - C wrapper，透過 git submodule 管理
- **Java FFI** (`native/generated`) - jextract 自動生成的 Java bindings
- **Clojure API** (`src/stock_dash/fubon/`) - 高階 Clojure 介面

**在 REPL 中使用：**

```clojure
(require '[stock-dash.fubon.sdk :as sdk])
(require '[stock-dash.fubon.lifecycle :as lifecycle])

;; 登入（自動初始化 SDK 並啟動 WebSocket consumer）
;; 對齊 C++ SDK：login(personal_id) -> vector<Account>
(def result (sdk/login! "身份證字號" "密碼" "path/to/cert.pfx"))
;=> {"A123456789" [{:name "現股帳戶"
;                   :branch-no "1234"
;                   :account "567890"
;                   :account-type "現股"}]}

;; 取得帳號列表
(def personal-id (first (keys result)))
(def accounts (get result personal-id))
(def first-account (first accounts))

;; 列出所有已登入的 personal-id
(sdk/list-personal-ids)
;=> ("A123456789")

;; 取得指定 personal-id 的所有帳號
(sdk/get-accounts "A123456789")
;=> [{:name "現股帳戶" :branch-no "1234" :account "567890" :account-type "現股"}]

;; 檢查狀態
(sdk/person-stats)
;=> {"A123456789" {:logged-in? true
;                   :account-count 1
;                   :channels-open [true]
;                   :consumers-running [true]}}

;; 查詢餘額（原始 API，直接使用 account map）
(sdk/bank-balance first-account)
;=> {:balance 123456 :available-balance 123456}

;; 便利函數：用 personal-id 查詢餘額（查詢第一個帳號）
(sdk/bank-balance-by-personal-id "A123456789")
;=> {:balance 123456 :available-balance 123456}

;; 模擬 WebSocket 訊息（測試）
(require '[stock-dash.fubon.websocket :as ws])
(ws/simulate-message! "A123456789" 0 {:type :quote :data {:symbol "2330" :price 650}})
;; 檢查 log 應該會看到 quote-received 訊息

;; 登出指定 personal-id
(sdk/logout! "A123456789")
;=> {:success true}

;; 或登出所有 personal-id
(sdk/logout-all!)
```

**設計原則：**
- ✅ **對齊原始 SDK**：完全對齊 C++ SDK 的 `login(personal_id) -> vector<Account>` 設計
- ✅ **以 personal-id 為核心**：login/logout 都以 personal-id 為單位操作
- ✅ **避免字串拼接**：不使用 "personal-id:branch-account" 這種字串作為 key
- ✅ **結構化資料**：使用 map 和 vector，而非字串作為複合 key

**重要特性：**
- ✅ **多身份證支援**：可以同時登入多個身份證（每個身份證可有多個帳號）
- ✅ **Reload 友善**：程式碼 reload 不影響連線和登入狀態
- ✅ **WebSocket consumer**：自動啟動 per-account 的訊息處理
- ✅ **Memory 安全**：使用 sliding-buffer 防止記憶體洩漏

**更新 fubon-sdk-ffi：**

當上游 C wrapper 更新時：

```bash
# 1. 更新 submodule
cd native/fubon-sdk-ffi
git pull origin main

# 2. 重新編譯
cd ../..
bash scripts/build-native.sh

# 3. 重新生成 Java bindings（如果 C API 有變更）
bash scripts/generate-bindings.sh

# 4. 重新編譯 Java classes
clj -T:build compile-java
```

**生命週期管理：**

SDK 使用 `defonce` + `atom` 模式管理狀態，並整合 `clj-reload` hooks：
- 程式碼 reload 時會自動重啟 SDK
- 應用程式啟動時自動初始化
- 符合專案的狀態管理慣例

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

建構流程會自動處理所有必要步驟：

```bash
# 建構獨立可執行的 jar 檔案
# 自動包含：
# - Java FFI bindings 編譯
# - Clojure AOT compilation
# - Native libraries (.dylib)
clj -T:build uber
```

建構完成後會產生 `target/stock-dash-0.1.0-standalone.jar`

**注意事項：**
- Uberjar 會包含 native libraries（`native/lib/*.dylib`）
- 執行時需要使用 `-Djava.library.path` 或確保 libraries 在正確位置

### 執行 Uberjar

```bash
# 方式 1: 使用 java.library.path（推薦）
java -Djava.library.path=native/lib \
     --enable-native-access=ALL-UNNAMED \
     -jar target/stock-dash-0.1.0-standalone.jar

# 方式 2: 設定環境變數
export DYLD_LIBRARY_PATH=native/lib  # macOS
java --enable-native-access=ALL-UNNAMED \
     -jar target/stock-dash-0.1.0-standalone.jar
```

### 編譯 Java Bindings

如果只需要編譯 Java classes（不建構完整 uberjar）：

```bash
clj -T:build compile-java
```

編譯後的 `.class` 檔案會產生在 `target/classes/com/fubon/ffi/`

### 清理建構產物

```bash
# 清理所有建構產物
clj -T:build clean

# 清理 Java bindings（需要重新執行 generate-bindings.sh）
rm -rf native/generated

# 清理 native libraries（需要重新執行 build-native.sh）
rm -rf native/lib/*.dylib
```

## 技術架構

### Panama FFI 整合

專案使用 **Java 25 Panama FFI** (正式版) 來與 C library 互動，取代傳統的 JNI 方式。

**優勢：**
- ✅ 不需要撰寫 JNI boilerplate 程式碼
- ✅ 類型安全的記憶體操作
- ✅ 自動生成 Java bindings (jextract)
- ✅ 更好的效能（相比 JNI）
- ✅ 現代化的 API 設計

**三層架構：**

```
┌─────────────────────────────────────┐
│  Clojure High-level API             │  stock-dash.fubon.sdk
│  (login!, bank-balance, etc.)       │  - Clojure idioms
│                                     │  - Error handling
└─────────────────┬───────────────────┘  - Data transformation
                  │
┌─────────────────▼───────────────────┐
│  Clojure FFI Layer                  │  stock-dash.fubon.ffi
│  (sdk-new, login, bank-remain)      │  - Memory management
│                                     │  - Struct parsing
└─────────────────┬───────────────────┘  - Arena management
                  │
┌─────────────────▼───────────────────┐
│  Java Panama FFI Bindings           │  com.fubon.ffi.*
│  (auto-generated by jextract)       │  - Native method handles
│                                     │  - Memory layouts
└─────────────────┬───────────────────┘  - Symbol lookup
                  │
┌─────────────────▼───────────────────┐
│  C Wrapper Library                  │  libfubon_c.dylib
│  (fubon-sdk-ffi)                    │  - C-compatible API
│                                     │  - Error handling
└─────────────────┬───────────────────┘  - Memory management
                  │
┌─────────────────▼───────────────────┐
│  Fubon C++ SDK                      │  libfubon.dylib
│  (uniffi-generated Rust bindings)   │  - Core business logic
│                                     │
└─────────────────────────────────────┘
```

**記憶體管理：**
- 使用 `Arena.ofShared()` 管理 Java 側記憶體
- C 函數回傳的記憶體使用 `fubon_free_*` 函數釋放
- 避免記憶體洩漏和 segfault

**生命週期整合：**
- 與 `clj-reload` 整合，reload 時自動重啟
- `defonce` + `atom` 模式管理狀態
- 符合專案的狀態管理慣例

### 檔案說明

**建置腳本：**
- `scripts/build-native.sh` - 編譯 C wrapper，自動修正 dylib 依賴路徑
- `scripts/generate-bindings.sh` - 使用 jextract 生成 Java bindings

**Clojure 程式碼：**
- `src/stock_dash/fubon/lifecycle.clj` - SDK 生命週期（init/shutdown/hooks）
- `src/stock_dash/fubon/ffi.clj` - 低階 FFI 呼叫（直接操作 MemorySegment）
- `src/stock_dash/fubon/sdk.clj` - 高階 API（Clojure idioms）

**配置檔案：**
- `deps.edn` - 加入 `native/generated` 到 classpath，設定 `java.library.path`
- `build.clj` - 加入 `compile-java` 函數編譯 Java bindings

## 常見問題

### 無法載入 libfubon_c.dylib

**錯誤訊息：**
```
Cannot open library: libfubon_c.dylib
```

**解決方案：**
1. 確認 native library 已編譯：
   ```bash
   ls -lh native/lib/
   # 應該看到 libfubon_c.dylib 和 libfubon.dylib
   ```

2. 確認 dylib 依賴路徑正確：
   ```bash
   otool -L native/lib/libfubon_c.dylib
   # 應該看到 @loader_path/libfubon.dylib
   ```

3. 如果路徑不正確，重新執行編譯腳本：
   ```bash
   bash scripts/build-native.sh
   ```

### Java restricted method 警告

**警告訊息：**
```
WARNING: A restricted method in java.lang.foreign.AddressLayout has been called
```

**解決方案：**
在啟動時加入 JVM 選項：
```bash
clj -J--enable-native-access=ALL-UNNAMED -M:run
```

或在 `deps.edn` 的 `:jvm-opts` 中加入（已預設加入 `:run` 和 `:repl` alias）。

### 更新 fubon-sdk-ffi 後編譯失敗

**解決方案：**
完整清理並重新建置：
```bash
# 1. 清理舊的產物
clj -T:build clean
rm -rf native/generated native/lib/*.dylib

# 2. 更新 submodule
cd native/fubon-sdk-ffi
git pull origin main
cd ../..

# 3. 重新建置所有內容
bash scripts/build-native.sh
bash scripts/generate-bindings.sh
clj -T:build compile-java
```

### REPL 中 reload 後 SDK 狀態遺失

這是正常行為！SDK 使用 `clj-reload` hooks：
- **reload 前**：自動呼叫 `before-reload` 停止 SDK
- **reload 後**：自動呼叫 `after-reload` 重啟 SDK

如果想手動控制：
```clojure
(require '[stock-dash.fubon.lifecycle :as fubon])

;; 手動停止
(fubon/stop-sdk!)

;; 手動啟動
(fubon/start-sdk!)
```

## License

Copyright © 2025