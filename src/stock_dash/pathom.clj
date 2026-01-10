(ns stock-dash.pathom
  (:require [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
            [com.wsscode.pathom3.connect.runner :as pcr]
            [com.brunobonacci.mulog :as mu]
            [stock-dash.config :as config]))

;; === 1. 模擬資料庫 ===
(def todos-db
  {1 {:todo/id 1 :todo/title "學習 Pathom3" :todo/completed false :todo/priority :high}
   2 {:todo/id 2 :todo/title "整合 Chassis" :todo/completed false :todo/priority :medium}
   3 {:todo/id 3 :todo/title "建立 DAG 範例" :todo/completed true :todo/priority :low}})

;; === 2. 資料 Resolvers ===
(pco/defresolver all-todos-resolver [_env _input]
  {::pco/output [{:todos/all [:todo/id]}]}
  {:todos/all (mapv (fn [id] {:todo/id id}) (keys todos-db))})

(pco/defresolver todo-by-id-resolver [_env {:todo/keys [id]}]
  {::pco/output [:todo/title :todo/completed :todo/priority]}
  ;; 模擬 IO 延遲以驗證平行執行和慢查詢記錄
  ;; (Thread/sleep 50)
  ;; (println "Fetching todo" id "at" (System/currentTimeMillis))
  (get todos-db id))

;; === 3. 計算 Resolvers ===
(pco/defresolver todo-status-resolver [_env {:todo/keys [completed]}]
  {::pco/output [:todo/status]}
  {:todo/status (if completed "已完成" "進行中")})

(pco/defresolver priority-label-resolver [_env {:todo/keys [priority]}]
  {::pco/output [:todo/priority-label]}
  {:todo/priority-label (case priority
                          :high "高優先度"
                          :medium "中優先度"
                          :low "低優先度"
                          "未知")})

;; === 4. View Resolver ===
(pco/defresolver todo-html-resolver [_env {:todo/keys [_id title status priority-label]}]
  {::pco/output [:todo/display-html]}
  {:todo/display-html
   (str "<li class=\"todo-item\">"
        "<strong>" title "</strong> "
        "<span class=\"status\">[" status "]</span> "
        "<span class=\"priority\">(" priority-label ")</span>"
        "</li>")})

;; === 5. 註冊 Resolvers ===
(def all-resolvers
  [all-todos-resolver
   todo-by-id-resolver
   todo-status-resolver
   priority-label-resolver
   todo-html-resolver])

(defonce registry (atom nil))

(defn start-pathom! []
  (reset! registry
    (-> {::p.a.eql/parallel? true}
        (pci/register all-resolvers)))
  (println "✓ Pathom registry initialized (parallel mode)"))

(defn stop-pathom! []
  (reset! registry nil)
  (println "✓ Pathom registry cleared"))

;; === 6. 查詢執行 ===
(defn- get-debug-config []
  (get-in (config/get-config) [:pathom :debug]))

(defn- log-slow-query! [query duration-ms stats]
  (let [cfg (get-debug-config)
        threshold (get cfg :slow-query-threshold-ms 100)]
    (when (and (get cfg :log-slow-queries)
               (> duration-ms threshold))
      (mu/log ::slow-query
              :query query
              :duration-ms duration-ms
              :threshold-ms threshold
              :resolver-count (count (::pcr/node-resolver-output stats))))))

(defn- log-query-error! [query error stats]
  (when (get-in (get-debug-config) [:log-errors])
    (mu/log ::query-error
            :query query
            :error-message (.getMessage error)
            :error-type (type error)
            :unreachable-paths (::pcr/unreachable-paths stats)
            :unreachable-resolvers (::pcr/unreachable-resolvers stats))))

(defn- log-full-stats! [query stats duration-ms]
  (when (get-in (get-debug-config) [:full-stats])
    (mu/log ::query-stats
            :query query
            :duration-ms duration-ms
            :stats stats)))

(defn process-eql
  "執行 EQL 查詢（平行模式，可選 debugging）"
  [query]
  (if-let [reg @registry]
    (let [debug-enabled? (get-in (get-debug-config) [:enabled])
          start-time (System/currentTimeMillis)
          result-promise (p.a.eql/process reg query)]
      (try
        (let [result @result-promise
              duration-ms (- (System/currentTimeMillis) start-time)]
          (when debug-enabled?
            (let [stats (-> result meta ::pcr/run-stats)]
              (log-full-stats! query stats duration-ms)
              (log-slow-query! query duration-ms stats)))
          result)
        (catch Exception e
          (when debug-enabled?
            (let [duration-ms (- (System/currentTimeMillis) start-time)
                  stats (try (-> @result-promise meta ::pcr/run-stats)
                            (catch Exception _ nil))]
              (log-query-error! query e stats)))
          (throw e))))
    (throw (ex-info "Pathom registry not initialized" {}))))

;; === 7. HTTP Response ===
(defn render-page []
  (let [result (process-eql
                [{:todos/all [:todo/id :todo/display-html]}])
        todos (get result :todos/all [])]
    {:status 200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body (str "<!DOCTYPE html>
<html>
<head>
  <meta charset=\"utf-8\">
  <title>Pathom3 DAG Demo</title>
  <style>
    body { font-family: sans-serif; max-width: 800px; margin: 40px auto; }
    h1 { color: #333; }
    .todo-item { margin: 10px 0; padding: 10px; border-left: 3px solid #0066cc; }
    .status { color: #666; margin-left: 10px; }
    .priority { color: #999; font-size: 0.9em; margin-left: 10px; }
  </style>
</head>
<body>
  <h1>Pathom3 DAG 範例</h1>
  <p>展示依賴解析和自動執行順序</p>
  <ul>"
                (apply str (map :todo/display-html todos))
                "</ul>
  <hr>
  <p><a href=\"/\">回首頁</a></p>
</body>
</html>")}))

;; === 8. Debug 輔助函數（REPL 使用）===
(defn process-eql-with-stats
  "執行查詢並回傳結果和詳細統計（用於 REPL debugging）"
  [query]
  (if-let [reg @registry]
    (let [start-time (System/currentTimeMillis)
          result @(p.a.eql/process reg query)
          duration-ms (- (System/currentTimeMillis) start-time)
          stats (-> result meta ::pcr/run-stats)]
      {:result result
       :duration-ms duration-ms
       :stats stats
       :resolver-count (count (::pcr/node-resolver-output stats))
       :unreachable-paths (::pcr/unreachable-paths stats)})
    (throw (ex-info "Pathom registry not initialized" {}))))

;; === 9. Reload Hooks (遵循 CLAUDE.md) ===
(defn ^:clj-reload/before-reload before-reload []
  (println "Reloading pathom namespace...")
  (stop-pathom!))

(defn ^:clj-reload/after-reload after-reload []
  (start-pathom!)
  (println "✓ Pathom reloaded"))
