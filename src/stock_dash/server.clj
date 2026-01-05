(ns stock-dash.server
  (:require [org.httpkit.server :as http]
            [stock-dash.config :as config]
            [stock-dash.logging :as log]))

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
    (log/log! ::server-started {:host host :port port})
    stop-fn))

(defn stop-server!
  "停止 web server"
  []
  (when-let [stop-fn @server]
    (stop-fn)
    (reset! server nil)
    (log/log! ::server-stopped)))

(defn server-running?
  "檢查 server 是否正在運行"
  []
  (some? @server))
