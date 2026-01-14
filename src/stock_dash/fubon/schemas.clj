(ns stock-dash.fubon.schemas
  (:require [malli.core :as m]
            [malli.error :as me]
            [malli.registry :as mr]
            [com.brunobonacci.mulog :as mu]
            [stock-dash.config :as config]))

(def registry
  (mr/composite-registry
   (m/default-schemas)
   {:fubon/account
    [:map
     [:name [:maybe :string]]
     [:branch-no :string]
     [:account :string]
     [:account-type [:maybe :string]]]

    :fubon/personal-id
    [:string {:min 10 :max 10}]

    :fubon/login-result
    [:map-of :fubon/personal-id [:vector :fubon/account]]

    :fubon/balance-result
    [:map
     [:balance :int]
     [:available-balance :int]]

    :fubon/inventory-odd
    [:map
     [:lastday-qty :int]
     [:buy-qty :int]
     [:buy-filled-qty :int]
     [:buy-value :int]
     [:today-qty :int]
     [:tradable-qty :int]
     [:sell-qty :int]
     [:sell-filled-qty :int]
     [:sell-value :int]]

    :fubon/inventory
    [:map
     [:date [:maybe :string]]
     [:account [:maybe :string]]
     [:branch-no [:maybe :string]]
     [:stock-no [:maybe :string]]
     [:order-type [:enum :stock :margin :short :sbl :day-trade :un-supported :un-defined]]
     [:lastday-qty :int]
     [:buy-qty :int]
     [:buy-filled-qty :int]
     [:buy-value :int]
     [:today-qty :int]
     [:tradable-qty :int]
     [:sell-qty :int]
     [:sell-filled-qty :int]
     [:sell-value :int]
     [:odd :fubon/inventory-odd]]

    :fubon/inventories-result
    [:vector :fubon/inventory]

    :fubon/market-type
    [:enum :common :fixing :odd :intraday-odd :emg :emg-odd :un-supported :un-defined]

    :fubon/symbol-quote
    [:map
     [:market [:maybe :string]]
     [:symbol [:maybe :string]]
     [:istib-or-psb :boolean]
     [:market-type :fubon/market-type]
     [:status {:optional true} [:maybe :int]]
     [:reference-price {:optional true} [:maybe :double]]
     [:unit :int]
     [:update-time [:maybe :string]]
     [:limitup-price {:optional true} [:maybe :double]]
     [:limitdown-price {:optional true} [:maybe :double]]
     [:open-price {:optional true} [:maybe :double]]
     [:high-price {:optional true} [:maybe :double]]
     [:low-price {:optional true} [:maybe :double]]
     [:last-price {:optional true} [:maybe :double]]
     [:total-volume {:optional true} [:maybe :int]]
     [:total-transaction {:optional true} [:maybe :int]]
     [:total-value {:optional true} [:maybe :int]]
     [:last-size {:optional true} [:maybe :int]]
     [:last-transaction {:optional true} [:maybe :int]]
     [:last-value {:optional true} [:maybe :int]]
     [:bid-price {:optional true} [:maybe :double]]
     [:bid-volume {:optional true} [:maybe :int]]
     [:ask-price {:optional true} [:maybe :double]]
     [:ask-volume {:optional true} [:maybe :int]]]}))

(mr/set-default-registry! registry)

(def sdk-schema
  [:multi {:dispatch first}
   [:login
    [:tuple :keyword
     [:map
      [:personal-id :fubon/personal-id]
      [:password :string]
      [:cert-path :string]
      [:cert-pass {:optional true} [:maybe :string]]]]]
   [:bank-balance
    [:tuple :keyword :fubon/account]]
   [:bank-balance-by-personal-id
    [:tuple :keyword :fubon/personal-id]]
   [:inventories
    [:tuple :keyword :fubon/account]]
   [:inventories-by-personal-id
    [:tuple :keyword :fubon/personal-id]]
   [:symbol-quote
    [:tuple :keyword
     [:map
      [:account :fubon/account]
      [:symbol :string]
      [:market-type {:optional true} :fubon/market-type]]]]
   [:symbol-quote-by-personal-id
    [:tuple :keyword :fubon/personal-id]]
   [:logout
    [:tuple :keyword :fubon/personal-id]]])

(def sdk-validator (m/validator sdk-schema))

(defn ensure!
  [dispatch-key params]
  (if (sdk-validator [dispatch-key params])
    true
    (let [env (-> (config/get-config) :app :env)]
      (if (= env :prod)
        (do
          (mu/log ::validation-failed :dispatch dispatch-key :input params)
          (throw (ex-info "Invalid input"
                          {:type dispatch-key
                           :message "輸入格式錯誤，請檢查參數"})))
        (let [explained (m/explain sdk-schema [dispatch-key params])
              humanized (me/humanize explained)
              error-detail (second humanized)]
          (throw (ex-info (str "Schema validation failed: " error-detail)
                          {:dispatch dispatch-key
                           :humanized error-detail
                           :explained explained
                           :input params})))))))


(comment
  (ensure! :login {:personal-id "123"
                   :password "xxx"
                   :cert-path "cert.pfx"})

  (ensure! :bank-balance {:branch-no "1234" :account "567890"})

  ;;
  )
