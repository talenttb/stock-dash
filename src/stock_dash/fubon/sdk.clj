(ns stock-dash.fubon.sdk
  (:require
   [clojure.java.io :as io]
   [com.brunobonacci.mulog :as mu]
   [stock-dash.fubon.ffi :as ffi]
   [stock-dash.fubon.lifecycle :as lifecycle]
   [stock-dash.fubon.websocket :as ws]))

(defn- mask-id [id]
  (if (> (count id) 6)
    (str (subs id 0 3) "***" (subs id (- (count id) 3)))
    "***"))

(defn login!
  [personal-id password cert-path & [cert-pass]]
  (let [sdk (lifecycle/get-sdk)
        ;; C SDK 需要絕對路徑
        absolute-cert-path (.getAbsolutePath (io/file cert-path))]
    (mu/trace ::login
      [:personal-id (mask-id personal-id)
       :cert-path absolute-cert-path]
      (let [result (ffi/login sdk personal-id password absolute-cert-path cert-pass)]
        (if (:success result)
          (let [accounts (:accounts result)]
            (lifecycle/register-person! personal-id accounts)
            (lifecycle/set-person-logged-in! personal-id true)
            (doseq [idx (range (count accounts))]
              (ws/start-consumer! personal-id idx))
            ;; TODO: 註冊 C callback
            ;; (ffi/register-ws-callback sdk personal-id)
            (mu/log ::login-success
                    :personal-id personal-id
                    :account-count (count accounts))
            {personal-id accounts})
          (let [error-msg (or (:error-msg result) "Unknown error")]
            (mu/log ::login-failed :error-msg error-msg)
            (throw (ex-info (str "登入失敗: " error-msg) result))))))))

(defn logout!
  "登出指定 personal-id

   注意：C SDK 沒有 logout API，只在 stop-sdk! 釋放資源"
  [personal-id]
  (when (lifecycle/get-accounts personal-id)
    (mu/trace ::logout [:personal-id personal-id]
      (try
        (lifecycle/unregister-person! personal-id)
        (mu/log ::logout-success :personal-id personal-id)
        {:success true}
        (catch Exception e
          (mu/log ::logout-failed
                  :personal-id personal-id
                  :error-message (.getMessage e))
          (throw (ex-info "登出失敗"
                          {:personal-id personal-id
                           :error e})))))))

(defn logout-all! []
  (doseq [personal-id (lifecycle/list-personal-ids)]
    (logout! personal-id)))

(defn bank-balance
  "查詢銀行帳戶餘額

   參數：account - {:name :branch-no :account :account-type}
   回傳：{:balance :available-balance}"
  [account]
  (let [sdk (lifecycle/get-sdk)]
    (mu/trace ::bank-balance
      [:account (:account account)]
      (let [result (ffi/bank-remain sdk account)]
        (if (:success result)
          (select-keys result [:balance :available-balance])
          (throw (ex-info "查詢餘額失敗" result)))))))

(defn bank-balance-by-personal-id
  "用 personal-id 查詢餘額（查第一個帳號）"
  [personal-id]
  (let [accounts (lifecycle/get-accounts personal-id)]
    (when-not accounts
      (throw (ex-info "找不到該身份證的已登入帳號"
                      {:personal-id personal-id
                       :available-personal-ids (lifecycle/list-personal-ids)})))
    (bank-balance (first accounts))))

(defn list-personal-ids []
  (lifecycle/list-personal-ids))

(defn get-accounts [personal-id]
  (lifecycle/get-accounts personal-id))

(defn find-personal-id-by-account
  "從 branch-no + account 找到 personal-id 和 account-index

   用於 callback 路由訊息到正確的 channel"
  [branch-no account]
  (lifecycle/find-personal-id-by-account branch-no account))

(defn person-stats []
  (lifecycle/person-stats))

(comment
  ;; === Fubon SDK 開發測試 ===

  (require '[stock-dash.config :as config])

  ;; 從 config 載入登入資訊
  (def fubon-config (-> (config/get-config) :fubon))
  (def personal-id (:personal-id fubon-config))

  ;; 登入
  (def result (login! personal-id (:password fubon-config) (:cert-path fubon-config) (:cert-pass fubon-config)))

  ;; 查看登入結果
  result
  ;=> {"A125218465" [{:name "..." :branch-no "1234" :account "567890" :account-type "現股"}]}

  ;; 取得帳號
  (def accounts (get result personal-id))
  (def first-account (first accounts))

  ;; 列出所有 personal-id
  (list-personal-ids)
  ;=> ("A125218465")

  ;; 取得指定 personal-id 的帳號
  (get-accounts personal-id)

  ;; 檢查狀態
  (person-stats)

  ;; 查詢銀行餘額
  (bank-balance first-account)
  (bank-balance-by-personal-id personal-id)

  ;; 登出
  (logout! personal-id)
  (logout-all!)

  ;; 完全關閉 SDK
  (lifecycle/stop-sdk!)

  ;;
  )
