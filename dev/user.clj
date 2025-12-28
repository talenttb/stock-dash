(ns user
  (:require [clj-reload.core :as reload]
            [clojure+.error :as error]
            [clojure+.print :as print]
            [clojure+.hashp :as hashp]
            [cprop.core :as cprop]))

(error/install!)
(print/install!)
(hashp/install!)

(reload/init {:dirs ["src" "dev"]})

(defonce config (atom nil))

(defn load-config []
  (reset! config (cprop/load-config))
  (println "✓ Configuration loaded")
  @config)

(defn reload []
  (reload/reload)
  (load-config))

;; Load config on startup
(load-config)
