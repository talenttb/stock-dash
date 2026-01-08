(ns user
  (:require [clj-reload.core :as reload]
            [clojure+.error :as error]
            [clojure+.print :as print]
            [clojure+.hashp :as hashp]
            [stock-dash.server :as server]
            [stock-dash.handler :as handler]
            [stock-dash.config :as config]))

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

(comment
  ;; 初次啟動
  (start)

  ;; 重載配置
  (config/load-config!)

  ;; 開發循環
  (restart)  ; 完整重啟：stop -> reset -> start
  (reset)    ; 只重載代碼

  ;;
  )

