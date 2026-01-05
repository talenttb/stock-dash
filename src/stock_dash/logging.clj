(ns stock-dash.logging
  (:require [com.brunobonacci.mulog :as μ]
            [clojure.java.io :as io]))

(defonce publishers (atom []))

(defn- init-global-context! []
  (let [get-config (requiring-resolve 'stock-dash.config/get-config)
        cfg (get-config)
        ctx {:app-name (get-in cfg [:app :name])
             :app-version (get-in cfg [:app :version])
             :env (or (System/getenv "APP_ENV") "development")
             :host (.getHostName (java.net.InetAddress/getLocalHost))}]
    (μ/set-global-context! ctx)))

(defn- start-console-publisher! []
  (μ/start-publisher! {:type :console
                       :pretty? true}))

(defn- start-file-publisher! [file-path]
  (let [log-file (io/file file-path)]
    (io/make-parents log-file)
    (μ/start-publisher! {:type :simple-file
                         :filename file-path})))

(defn- init-publishers! []
  (when (empty? @publishers)
    (init-global-context!)
    (let [get-config (requiring-resolve 'stock-dash.config/get-config)
          cfg (get-config)
          console-enabled? (get-in cfg [:logging :console :enabled] true)
          file-enabled? (get-in cfg [:logging :file :enabled] true)
          file-path (get-in cfg [:logging :file :path] "workspace/app.log")
          pubs (cond-> []
                 console-enabled? (conj (start-console-publisher!))
                 file-enabled? (conj (start-file-publisher! file-path)))]
      (reset! publishers pubs))))

(defn log!
  ([event-name] (log! event-name {}))
  ([event-name data]
   (μ/log event-name data)))

(defn log-error!
  ([event-name error] (log-error! event-name error {}))
  ([event-name error data]
   (μ/log event-name
          (merge data
                 {:error-type (str (type error))
                  :error-message (.getMessage error)
                  :error-stacktrace (mapv str (.getStackTrace error))}))))

(defn stop-publishers!
  "停止所有 publishers"
  []
  (doseq [pub @publishers]
    (pub))
  (reset! publishers []))

(defn restart-publishers!
  "重新初始化 publishers"
  []
  (stop-publishers!)
  (init-publishers!))

(init-publishers!)
