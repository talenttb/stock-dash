(ns stock-dash.repl
  (:require [com.brunobonacci.mulog :as mu]
            [stock-dash.pathom :as pathom]))

;; ============================================================
;; 日誌測試
;; ============================================================

(defn test-log
  "測試日誌記錄"
  []
  (mu/log ::test-event
          :test-data "hello from REPL"
          :timestamp (System/currentTimeMillis)))

;; ============================================================
;; Pathom 測試
;; ============================================================

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

;; ============================================================
;; 使用範例
;; ============================================================

(comment
  ;; 日誌測試
  (test-log)

  ;; Pathom 基本測試
  (test-pathom)
  (test-pathom-simple)

  ;; Pathom 特定查詢
  (pathom/process-eql [{[:todo/id 1] [:todo/title :todo/status :todo/priority-label]}])
  (pathom/process-eql [{:todos/all [:todo/display-html]}])

  ;; Pathom Debugging
  (test-pathom-with-stats)
  ;; => {:result {...} :duration-ms 5 :stats {...} :resolver-count 15}

  (:duration-ms (test-pathom-with-stats))
  (:resolver-count (test-pathom-with-stats))

  ;;
  )
