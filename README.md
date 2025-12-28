# stock-dash

A Clojure application for stock dashboard.

## 專案結構

```
stock-dash/
├── deps.edn                    # 依賴管理檔案
├── build.clj                   # 建構腳本
├── src/
│   └── stock_dash/
│       └── core.clj           # 主程式
└── test/
    └── stock_dash/
        └── core_test.clj      # 測試檔案
```

## 開發

### 執行程式

```bash
# 直接執行
clj -M:run

# 啟動 REPL
clj

# 啟動 nREPL (支援 CIDER)
clj -M:repl
```

### 執行測試

```bash
clj -M:test -m clojure.test
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