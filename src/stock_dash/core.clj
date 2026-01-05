(ns stock-dash.core
  (:require [stock-dash.server :as server]
            [stock-dash.handler :as handler]
            [stock-dash.logging :as log])
  (:gen-class))

(defn -main
  [& args]
  (log/log! ::app-starting {:args args})
  (try
    (server/start-server! #'handler/app)
    (log/log! ::app-started)
    @(promise)
    (catch Exception e
      (log/log-error! ::app-start-failed e)
      (System/exit 1))))
