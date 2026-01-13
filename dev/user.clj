(ns user
  (:require [clj-reload.core :as reload]
            [clojure+.error :as error]
            [clojure+.print :as print]
            [clojure+.hashp :as hashp]
            [stock-dash.server :as server]
            [stock-dash.handler :as handler]
            [stock-dash.config :as config]
            [stock-dash.portal :as portal]))

(error/install!)
(print/install!)
(hashp/install!)

(reload/init {:dirs ["src" "dev"]})

(defn reset
  "重載代碼和配置（各 namespace 的 reload hooks 會自動處理狀態重啟）"
  []
  (reload/reload)
  (println "✓ Code reloaded"))

(defn start
  "啟動 web server"
  []
  (server/start-server! #'handler/app))

(defn stop
  "停止 web server"
  []
  (server/stop-server!))

(defn restart
  "重載代碼並重啟 web server"
  []
  (stop)
  (reset)
  (start))

(defn portal
  "手動打開 Portal UI"
  []
  (portal/open!))

(defn cleanup
  "清理資源（jack-out 前呼叫）

   清理步驟：
   1. 登出所有 Fubon 帳號
   2. 關閉 Fubon SDK
   3. 停止 web server

   適用場景：
   - Jack-out 前手動清理
   - 開發中需要重新初始化

   注意：此函數不會終止 REPL process"
  []
  (println "Cleaning up resources...")
  (try
    ;; 登出所有 Fubon 帳號
    (require '[stock-dash.fubon.sdk :as fubon-sdk])
    (require '[stock-dash.fubon.lifecycle :as fubon-lifecycle])
    (when ((resolve 'stock-dash.fubon.lifecycle/any-logged-in?))
      ((resolve 'stock-dash.fubon.sdk/logout-all!))
      (println "✓ Logged out all Fubon accounts"))

    ;; 關閉 Fubon SDK
    (when ((resolve 'stock-dash.fubon.lifecycle/sdk-initialized?))
      ((resolve 'stock-dash.fubon.lifecycle/stop-sdk!))
      (println "✓ Fubon SDK stopped"))

    ;; 停止 web server
    (stop)

    (println "✓ Cleanup completed")
    (catch Exception e
      (println "✗ Cleanup error:" (.getMessage e)))))

(comment
  ;; 初次啟動
  (start)   ; 會自動啟動 Portal (但不開啟 UI)
  (portal)  ; 手動打開 Portal UI

  ;; 重載配置
  (config/load-config!)

  ;; 開發循環
  (restart)  ; 完整重啟：stop -> reset -> start
  (reset)    ; 只重載代碼

  ;; Jack-out 前清理（重要！）
  (cleanup)

  ;;
  )

