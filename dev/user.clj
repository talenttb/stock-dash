(ns user
  (:require [clj-reload.core :as reload]
            [clojure+.error :as error]
            [clojure+.print :as print]
            [clojure+.hashp :as hashp]
            [com.brunobonacci.mulog :as mu]
            [stock-dash.server :as server]
            [stock-dash.handler :as handler]
            [stock-dash.config :as config]
            [stock-dash.logging :as log]))

(error/install!)
(print/install!)
(hashp/install!)

(reload/init {:dirs ["src" "dev"]})

(defn reset
  "重載代碼、配置和日誌系統"
  []
  (reload/reload)
  (config/load-config!)
  (log/restart-publishers!)
  (println "✓ Code, config, and logging reloaded"))

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

(defn test-log
  "測試日誌記錄"
  []
  (mu/log ::test-event
          :test-data "hello from REPL"
          :timestamp (System/currentTimeMillis)))

(comment
  (restart)
  (reset)
  (test-log)
  ;;
  )

