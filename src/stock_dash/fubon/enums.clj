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
