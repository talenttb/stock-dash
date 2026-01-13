(ns stock-dash.portal
  (:require [portal.api :as p]))

(defonce portal-instance (atom nil))

(defn start!
  "啟動 Portal 並設定為 tap> 的目標（不自動打開 UI）"
  []
  (when-not @portal-instance
    (let [get-config (requiring-resolve 'stock-dash.config/get-config)
          cfg (get-config)
          port (get-in cfg [:portal :port])
          #_#_launcher (get-in cfg [:portal :launcher])
          options (cond-> {:launcher false}
                    port (assoc :port port))
          instance (p/open options)]
      (reset! portal-instance instance)
      (add-tap #'p/submit)
      #_(println (str "✓ Portal started"
                    (when port (str " on port " port))
                    (when launcher (str " (configured for " (name launcher) ")"))
                    " - use 'open!' to launch UI")))))

(defn open!
  "手動打開 Portal UI（根據配置的 launcher 類型開啟）"
  []
  (if @portal-instance
    (let [get-config (requiring-resolve 'stock-dash.config/get-config)
          cfg (get-config)
          launcher (get-in cfg [:portal :launcher])]
      (if launcher
        ;; 使用配置的 launcher 重新開啟
        (p/open (assoc @portal-instance :launcher launcher))
        ;; 預設行為：開啟瀏覽器
        (p/open @portal-instance))
      (println (str "✓ Portal UI opened" (when launcher (str " in " (name launcher))))))
    (println "✗ Portal not started. Call 'start!' first.")))

(defn stop!
  "停止 Portal"
  []
  (when @portal-instance
    (remove-tap #'p/submit)
    (p/close @portal-instance)
    (reset! portal-instance nil)
    (println "✓ Portal stopped")))
