(ns stock-dash.core
  (:require [com.brunobonacci.mulog :as mu]
            [stock-dash.server :as server]
            [stock-dash.handler :as handler]
            [stock-dash.logging :as log]
            [stock-dash.pathom :as pathom])
  (:gen-class))

(defn -main
  [& args]
  (mu/log ::app-starting :args args)
  (try
    (pathom/start-pathom!)
    (server/start-server! #'handler/app)
    (mu/log ::app-started)
    @(promise)
    (catch Exception e
      (log/log-error! ::app-start-failed e)
      (System/exit 1))))
