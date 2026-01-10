(ns stock-dash.portal
  (:require [portal.api :as p]))

(defonce portal-instance (atom nil))

(defn start!
  "啟動 Portal 並設定為 tap> 的目標"
  []
  (when-not @portal-instance
    (let [get-config (requiring-resolve 'stock-dash.config/get-config)
          cfg (get-config)
          port (get-in cfg [:portal :port])
          instance (p/open (if port {:port port} {}))]
      (reset! portal-instance instance)
      (add-tap #'p/submit)
      (println (str "✓ Portal started on port " (or port "auto"))))))

(defn stop!
  "停止 Portal"
  []
  (when @portal-instance
    (remove-tap #'p/submit)
    (p/close @portal-instance)
    (reset! portal-instance nil)
    (println "✓ Portal stopped")))
