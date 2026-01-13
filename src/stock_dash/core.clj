(ns stock-dash.core
  (:require [com.brunobonacci.mulog :as mu]
            [stock-dash.server :as server]
            [stock-dash.handler :as handler]
            [stock-dash.logging :as log]
            [stock-dash.pathom :as pathom]
            [stock-dash.fubon.lifecycle :as fubon])
  (:gen-class))

(defn -main
  [& args]
  (mu/log ::app-starting :args args)
  (try
    (pathom/start-pathom!)
    ;; Fubon SDK 會在第一次使用時自動初始化（delay）
    ;; 不需要顯式呼叫 fubon/start-sdk!
    (server/start-server! #'handler/app)
    (mu/log ::app-started)

    ;; 註冊 shutdown hook - 確保應用程式關閉時正確釋放資源
    (.addShutdownHook (Runtime/getRuntime)
      (Thread. (fn []
                 (println "Shutting down application...")
                 (try
                   ;; 登出所有帳號（停止 consumer、關閉 channel）
                   (require '[stock-dash.fubon.sdk :as fubon-sdk])
                   ((resolve 'fubon-sdk/logout-all!))
                   ;; 關閉 SDK
                   (fubon/stop-sdk!)
                   (println "✓ Cleanup completed")
                   (catch Exception e
                     (println "✗ Shutdown error:" (.getMessage e)))))))

    @(promise)
    (catch Exception e
      (log/log-error! ::app-start-failed e)
      (System/exit 1))))
