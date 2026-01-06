(ns stock-dash.handler
  (:require [reitit.ring :as ring]
            [jsonista.core :as json]
            [com.brunobonacci.mulog :as mu]
            [stock-dash.logging :as log]))

(defn json-response
  ([data] (json-response 200 data))
  ([status data]
   {:status status
    :headers {"Content-Type" "application/json; charset=utf-8"}
    :body (json/write-value-as-string data)}))

(defn home-page
  [_request]
  {:status 200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body "<!DOCTYPE html>
<html>
<head>
  <meta charset=\"utf-8\">
  <title>Stock Dashboard</title>
</head>
<body>
  <h1>Stock Dashboard</h1>
  <p>Welcome to Stock Dashboard</p>
  <ul>
    <li><a href=\"/api/health\">API Health Check</a></li>
    <li><a href=\"/api/status\">API Status</a></li>
  </ul>
</body>
</html>"})

(defn health-check
  [_request]
  (mu/log ::operation-start :message "開始健康檢查")

  (let [timestamp (System/currentTimeMillis)
        _ (mu/log ::timestamp-generated :timestamp timestamp)

        response (mu/trace ::json-parser
                   [:source "json-parser"]
                   (json-response {:status "ok"
                                   :timestamp timestamp}))]

    (mu/log ::data-fetched
            :status (:status response)
            :has-body (some? (:body response)))

    (mu/log ::operation-complete :result "success")

    (mu/trace ::health-check-return
      [:final-status (:status response)]
      response)))

(defn status
  [_request]
  (json-response {:app "stock-dash"
                  :version "0.1.0"
                  :uptime (.. java.lang.management.ManagementFactory
                              getRuntimeMXBean
                              getUptime)}))

(defn wrap-errors
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (log/log-error! ::handler-error e
                        :uri (:uri request)
                        :method (:request-method request))
        (json-response 500 {:error (.getMessage e)
                            :type (str (type e))})))))

(defn home-handler
  [{:keys [request-method] :as request}]
  (case request-method
    :get (home-page request)
    (json-response 405 {:error "Method not allowed"
                        :allowed-methods ["GET"]})))

(defn health-handler
  [{:keys [request-method] :as request}]
  (case request-method
    :get (health-check request)
    (json-response 405 {:error "Method not allowed"
                        :allowed-methods ["GET"]})))

(defn status-handler
  [{:keys [request-method] :as request}]
  (case request-method
    :get (status request)
    (json-response 405 {:error "Method not allowed"
                        :allowed-methods ["GET"]})))

(defn wrap-request-logging
  [handler]
  (fn [request]
    (let [{:keys [request-method uri query-string headers remote-addr]} request
          user-agent (get headers "user-agent")
          referer (get headers "referer")]
      (mu/with-context {:method request-method
                        :uri uri
                        :query-string query-string
                        :user-agent user-agent
                        :referer referer
                        :remote-addr remote-addr}
        (mu/trace ::http-request
          []
          (handler request))))))

(def routes
  [["/" {:handler #'home-handler}]
   ["/api"
    ["/health" {:handler #'health-handler}]
    ["/status" {:handler #'status-handler}]]])

(def app
  (-> (ring/ring-handler
       (ring/router routes)
       (ring/create-default-handler))
      (wrap-errors)
      (wrap-request-logging)))
