(ns stock-dash.handler
  (:require [reitit.ring :as ring]
            [jsonista.core :as json]))

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
  (json-response {:status "ok"
                  :timestamp (System/currentTimeMillis)}))

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

(def routes
  [["/" {:handler #'home-handler}]
   ["/api"
    ["/health" {:handler #'health-handler}]
    ["/status" {:handler #'status-handler}]]])

(def app
  (-> (ring/ring-handler
       (ring/router routes)
       (ring/create-default-handler))
      (wrap-errors)))
