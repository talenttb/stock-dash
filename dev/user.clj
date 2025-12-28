(ns user
  (:require [clj-reload.core :as reload]
            [clojure+.error :as error]
            [clojure+.print :as print]
            [clojure+.hashp :as hashp]
            [stock-dash.config :as config]
            [stock-dash.server :as server]
            [stock-dash.handler :as handler]))

(error/install!)
(print/install!)
(hashp/install!)

(reload/init {:dirs ["src" "dev"]})

(defn reload []
  (reload/reload))

(defn start
  "啟動 web server"
  []
  (server/start-server! #'handler/app))

(defn stop
  "停止 web server"
  []
  (server/stop-server!))

(defn restart
  "重啟 web server"
  []
  (stop)
  (start))
