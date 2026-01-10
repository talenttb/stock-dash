(ns verify-portal
  (:require [clojure.string :as str]))

(println "\n=== Portal 整合驗證 ===\n")

;; 1. 驗證 Portal 依賴在 classpath
(print "1. 檢查 Portal 在 classpath... ")
(try
  (require 'portal.api)
  (println "✓")
  (catch Exception e
    (println "✗")
    (println "   錯誤: Portal 不在 classpath 中")
    (System/exit 1)))

;; 2. 驗證 config.edn 包含 portal 配置
(print "2. 檢查 config.edn... ")
(let [config-content (slurp "resources/config.edn")]
  (if (str/includes? config-content ":portal")
    (println "✓")
    (do
      (println "✗")
      (println "   錯誤: config.edn 沒有 :portal 配置")
      (System/exit 1))))

;; 3. 驗證 portal.clj 可以編譯
(print "3. 驗證 portal.clj 語法... ")
(try
  (require 'stock-dash.portal)
  (println "✓")
  (catch Exception e
    (println "✗")
    (println "   錯誤:" (.getMessage e))
    (System/exit 1)))

;; 4. 驗證 server.clj 可以編譯
(print "4. 驗證 server.clj 語法... ")
(try
  (require 'stock-dash.server)
  (println "✓")
  (catch Exception e
    (println "✗")
    (println "   錯誤:" (.getMessage e))
    (System/exit 1)))

;; 5. 驗證 portal 函數存在
(print "5. 檢查 Portal 函數... ")
(let [portal-ns (find-ns 'stock-dash.portal)
      start-fn (ns-resolve portal-ns 'start!)
      stop-fn (ns-resolve portal-ns 'stop!)]
  (if (and start-fn stop-fn)
    (println "✓")
    (do
      (println "✗")
      (println "   錯誤: portal/start! 或 portal/stop! 不存在")
      (System/exit 1))))

;; 6. 驗證配置可以讀取
(print "6. 驗證配置讀取... ")
(try
  (require 'stock-dash.config)
  (let [get-config (resolve 'stock-dash.config/get-config)
        cfg (get-config)
        portal-port (get-in cfg [:portal :port])]
    (if (= 5678 portal-port)
      (println "✓")
      (do
        (println "✗")
        (println "   錯誤: Portal port 不是 5678，實際值:" portal-port)
        (System/exit 1))))
  (catch Exception e
    (println "✗")
    (println "   錯誤:" (.getMessage e))
    (System/exit 1)))

(println "\n=== 所有驗證通過！✓ ===\n")
(println "建議手動測試：")
(println "  1. 執行: clj -M:repl")
(println "  2. 在 REPL 中執行: (start)")
(println "  3. 檢查 Portal UI: http://localhost:5678")
(println "  4. 測試: (tap> {:test \"data\"})")
(println "  5. 在 Portal UI 中查看資料")
(println)

(System/exit 0)
