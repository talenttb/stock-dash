(ns stock-dash.config
  (:require
   [clojure.java.io :as io]
   [cprop.core :as cprop]))

(defonce config (atom nil))

(defn load-config! []
  (let [base-config (cprop/load-config :resource "config.edn"
                                       :file "workspace/secret.edn")
        extra-config-path (System/getenv "EXTRA_CONFIG_PATH")
        final-config (if (and extra-config-path
                             (.exists (io/file extra-config-path)))
                       (do
                         (println "Loading extra config from:" extra-config-path)
                         (merge base-config (cprop/load-config :file extra-config-path)))
                       base-config)]
    (reset! config final-config)
    @config))

(defn get-config []
  @config)

;; clj-reload hooks
(defn ^:clj-reload/before-reload before-reload []
  (println "Reloading configuration..."))

(defn ^:clj-reload/after-reload after-reload []
  (load-config!)
  (println "✓ Configuration reloaded"))

;; Initialize config on namespace load
(load-config!)
