(ns stock-dash.core
  (:require [stock-dash.server :as server]
            [stock-dash.handler :as handler])
  (:gen-class))

(defn -main
  [& args]
  (server/start-server! #'handler/app)
  @(promise))
