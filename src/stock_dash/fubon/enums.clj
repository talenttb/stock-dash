(ns stock-dash.fubon.enums
  (:require
   [clojure.set :as set]))

(def order-type-to-int
  {:stock 1
   :margin 2
   :short 3
   :sbl 4
   :day-trade 5
   :un-supported 6
   :un-defined 7})

(def int-to-order-type
  (set/map-invert order-type-to-int))

(defn int->order-type
  [n]
  (int-to-order-type n n))

(defn order-type->int
  [kw]
  (order-type-to-int kw))

;; === MarketType enum ===

(def market-type-to-int
  {:common 1
   :fixing 2
   :odd 3
   :intraday-odd 4
   :emg 5
   :emg-odd 6
   :un-supported 7
   :un-defined 8})

(def int-to-market-type
  (set/map-invert market-type-to-int))

(defn int->market-type
  "將 C 層的整數 market-type 轉換為 Clojure keyword"
  [n]
  (int-to-market-type n n))

(defn market-type->int
  "將 Clojure keyword market-type 轉換為 C 層的整數"
  [kw]
  (market-type-to-int kw 8))
