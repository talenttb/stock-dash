(ns stock-dash.config
  (:require [cprop.core :as cprop]))

(defonce config (atom nil))

(defn load-config! []
  (reset! config (cprop/load-config))
  @config)

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
