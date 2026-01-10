(ns stock-dash.server
  (:require [org.httpkit.server :as http]
            [com.brunobonacci.mulog :as mu]
            [stock-dash.config :as config]
            [stock-dash.portal :as portal]))

(defonce ^:private server (atom nil))

(defn stop-server!
  "停止 web server"
  []
  (when-let [stop-fn @server]
    (portal/stop!)
    (stop-fn)
    (reset! server nil)
    (mu/log ::server-stopped)))

(defn start-server!
  "啟動 web server，如果已在運行則先停止"
  [handler]
  (when @server
    (mu/log ::server-already-running :action "stopping-first")
    (stop-server!))
  (let [cfg (config/get-config)
        host (get-in cfg [:server :host])
        port (get-in cfg [:server :port])
        stop-fn (http/run-server
                 handler
                 {:ip host
                  :port port})]
    (reset! server stop-fn)
    (portal/start!)
    (mu/log ::server-started :host host :port port)
    stop-fn))

(defn server-running?
  "檢查 server 是否正在運行"
  []
  (some? @server))
