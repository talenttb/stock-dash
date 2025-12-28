(ns user
  (:require [clj-reload.core :as reload]
            [clojure+.error :as error]
            [clojure+.print :as print]
            [clojure+.hashp :as hashp]
            [stock-dash.config :as config]))

(error/install!)
(print/install!)
(hashp/install!)

(reload/init {:dirs ["src" "dev"]})

(defn reload []
  (reload/reload))
