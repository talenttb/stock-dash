(ns stock-dash.server
  (:require [org.httpkit.server :as http]
            [stock-dash.config :as config]))

(defonce ^:private server (atom nil))

(defn start-server!
  "啟動 web server"
  [handler]
  (when @server
    (throw (ex-info "Server is already running" {:server @server})))
  (let [cfg (config/get-config)
        host (get-in cfg [:server :host])
        port (get-in cfg [:server :port])
        stop-fn (http/run-server
                 handler
                 {:ip host
                  :port port})]
    (reset! server stop-fn)
    (println (str "Server started on http://" host ":" port))
    stop-fn))

(defn stop-server!
  "停止 web server"
  []
  (when-let [stop-fn @server]
    (stop-fn)
    (reset! server nil)
    (println "Server stopped")))
