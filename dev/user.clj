(ns user
  (:require [clj-reload.core :as reload]
            [clojure+.error :as error]
            [clojure+.print :as print]
            [clojure+.hashp :as hashp]
            [com.brunobonacci.mulog :as mu]
            [stock-dash.server :as server]
            [stock-dash.handler :as handler]
            [stock-dash.config :as config]
            [stock-dash.logging :as log]
            [stock-dash.pathom :as pathom]))

(error/install!)
(print/install!)
(hashp/install!)

(reload/init {:dirs ["src" "dev"]})

(defn reset
  "重載代碼和配置（各 namespace 的 reload hooks 會自動處理狀態重啟）"
  []
  (reload/reload)
  (println "✓ Code reloaded"))

(defn start
  "啟動 web server"
  []
  (server/start-server! #'handler/app))

(defn stop
  "停止 web server"
  []
  (server/stop-server!))

(defn restart
  "重載代碼並重啟 web server"
  []
  (stop)
  (reset)
  (start))

(defn test-log
  "測試日誌記錄"
  []
  (mu/log ::test-event
          :test-data "hello from REPL"
          :timestamp (System/currentTimeMillis)))

(defn test-pathom
  "測試 Pathom EQL 查詢"
  []
  (pathom/process-eql
   [{:todos/all [:todo/id :todo/title :todo/status :todo/priority-label]}]))

(defn test-pathom-simple
  "測試簡單查詢"
  []
  (pathom/process-eql
   [{[:todo/id 1] [:todo/title :todo/status]}]))

(defn test-pathom-with-stats
  "測試查詢並顯示詳細統計（debugging 用）"
  []
  (pathom/process-eql-with-stats
   [{:todos/all [:todo/id :todo/title :todo/status :todo/priority-label]}]))

(comment
  (restart)
  (reset)
  (test-log)

  ;; Pathom 測試
  (test-pathom)
  (test-pathom-simple)

  ;; 測試單一 todo
  (pathom/process-eql [{[:todo/id 1] [:todo/title :todo/status :todo/priority-label]}])

  ;; 測試 HTML 渲染
  (pathom/process-eql [{:todos/all [:todo/display-html]}])

  ;; Debugging: 查看詳細統計
  (test-pathom-with-stats)
  ;; => {:result {...} :duration-ms 5 :stats {...} :resolver-count 15}

  ;; Debugging: 只看執行時間
  (:duration-ms (test-pathom-with-stats))

  ;; Debugging: 查看 resolver 數量
  (:resolver-count (test-pathom-with-stats))
  ;;
  )

