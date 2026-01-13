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
     [:available-balance :int]]}))

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
