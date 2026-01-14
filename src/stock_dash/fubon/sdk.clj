(ns stock-dash.fubon.sdk
  (:require
   [clojure.java.io :as io]
   [com.brunobonacci.mulog :as mu]
   [stock-dash.fubon.ffi :as ffi]
   [stock-dash.fubon.lifecycle :as lifecycle]
   [stock-dash.fubon.schemas :as schemas]
   [stock-dash.fubon.websocket :as ws]))

(defn- mask-id [id]
  (if (> (count id) 6)
    (str (subs id 0 3) "***" (subs id (- (count id) 3)))
    "***"))

(defn login!
  [personal-id password cert-path & [cert-pass]]
  (schemas/ensure! :login {:personal-id personal-id
                           :password password
                           :cert-path cert-path
                           :cert-pass cert-pass})

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
  [personal-id]
  (schemas/ensure! :logout personal-id)

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
  [account]
  (schemas/ensure! :bank-balance account)

  (let [sdk (lifecycle/get-sdk)]
    (mu/trace ::bank-balance
      [:account (:account account)]
      (let [result (ffi/bank-remain sdk account)]
        (if (:success result)
          (select-keys result [:balance :available-balance])
          (throw (ex-info "查詢餘額失敗" result)))))))

(defn bank-balance-by-personal-id
  [personal-id]
  (schemas/ensure! :bank-balance-by-personal-id personal-id)

  (let [accounts (lifecycle/get-accounts personal-id)]
    (when-not accounts
      (throw (ex-info "找不到該身份證的已登入帳號"
                      {:personal-id personal-id
                       :available-personal-ids (lifecycle/list-personal-ids)})))
    (bank-balance (first accounts))))

(defn inventories
  [account]
  (schemas/ensure! :inventories account)

  (let [sdk (lifecycle/get-sdk)]
    (mu/trace ::inventories
      [:account (:account account)]
      (let [result (ffi/inventories sdk account)]
        (if (:success result)
          (:inventories result)
          (throw (ex-info "查詢庫存失敗" result)))))))

(comment
  ;; inventories 回傳欄位說明（對應 C SDK FubonInventory）
  ;;
  ;; 基本資訊
  ;; :date         日期
  ;; :account      帳號
  ;; :branch-no    分公司代號
  ;; :stock-no     股票代號
  ;; :order-type   委託類型（:stock 現股, :margin 融資, :short 融券 等）
  ;;
  ;; 整股部位
  ;; :lastday-qty     昨日庫存量
  ;; :today-qty       今日庫存量
  ;; :tradable-qty    可交易量
  ;; :buy-qty         買進委託量
  ;; :buy-filled-qty  買進成交量
  ;; :buy-value       買進金額
  ;; :sell-qty        賣出委託量
  ;; :sell-filled-qty 賣出成交量
  ;; :sell-value      賣出金額
  ;;
  ;; 零股部位（:odd 內嵌結構，欄位同上）
  ;; :odd {:lastday-qty :today-qty :tradable-qty
  ;;       :buy-qty :buy-filled-qty :buy-value
  ;;       :sell-qty :sell-filled-qty :sell-value}
  )

(defn inventories-by-personal-id
  [personal-id]
  (schemas/ensure! :inventories-by-personal-id personal-id)

  (let [accounts (lifecycle/get-accounts personal-id)]
    (when-not accounts
      (throw (ex-info "找不到該身份證的已登入帳號"
                      {:personal-id personal-id
                       :available-personal-ids (lifecycle/list-personal-ids)})))
    (inventories (first accounts))))

(defn list-personal-ids []
  (lifecycle/list-personal-ids))

(defn get-accounts [personal-id]
  (lifecycle/get-accounts personal-id))

(defn find-personal-id-by-account
  [branch-no account]
  (lifecycle/find-personal-id-by-account branch-no account))

(defn person-stats []
  (lifecycle/person-stats))

(defn symbol-quote
  [account symbol & [market-type]]
  (schemas/ensure! :symbol-quote (cond-> {:account account :symbol symbol}
                                    market-type (assoc :market-type market-type)))

  (let [sdk (lifecycle/get-sdk)]
    (mu/trace ::symbol-quote
      [:account (:account account) :symbol symbol :market-type market-type]
      (let [result (ffi/query-symbol-quote sdk account symbol market-type)]
        (if (:success result)
          (:quote result)
          (throw (ex-info "查詢報價失敗" result)))))))

(comment
  ;; symbol-quote 回傳欄位說明（對應 C SDK FubonSymbolQuote）
  ;;
  ;; 價格相關
  ;; :last-price       最新成交價
  ;; :open-price       開盤價
  ;; :high-price       最高價
  ;; :low-price        最低價
  ;; :reference-price  參考價（通常是昨收）
  ;; :limitup-price    漲停價
  ;; :limitdown-price  跌停價
  ;;
  ;; 五檔報價（最佳一檔）
  ;; :bid-price        買進價（最高買價）
  ;; :bid-volume       買進量（張）
  ;; :ask-price        賣出價（最低賣價）
  ;; :ask-volume       賣出量（張）
  ;;
  ;; 成交量值
  ;; :total-volume      總成交量（張）
  ;; :total-value       總成交金額
  ;; :total-transaction 總成交筆數
  ;; :last-size         最後一筆成交量
  ;; :last-value        最後一筆成交金額
  ;; :last-transaction  最後成交筆數
  ;;
  ;; 其他
  ;; :symbol           股票代號
  ;; :market           市場（如 "TAIEX"）
  ;; :market-type      市場類型（:common 普通股等）
  ;; :unit             交易單位（1張=1000股）
  ;; :status           交易狀態碼
  ;; :update-time      更新時間
  ;; :istib-or-psb     是否為 TIB/PSB 標的
  )

(defn symbol-quote-by-personal-id
  [personal-id symbol & [market-type]]
  (schemas/ensure! :symbol-quote-by-personal-id personal-id)

  (let [accounts (lifecycle/get-accounts personal-id)]
    (when-not accounts
      (throw (ex-info "找不到該身份證的已登入帳號"
                      {:personal-id personal-id
                       :available-personal-ids (lifecycle/list-personal-ids)})))
    (symbol-quote (first accounts) symbol market-type)))

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
  (def first-account {:name "劉力宇", :branch-no "20508", :account "3529847", :account-type "stock"})

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

  ;; 查詢股票庫存
  (inventories first-account)
  (inventories-by-personal-id personal-id)

  ;; 查詢個股報價
  (symbol-quote first-account "2330")
  (symbol-quote first-account "2330" :common)
  (symbol-quote-by-personal-id personal-id "2330")

  ;; 登出
  (logout! personal-id)
  (logout-all!)

  ;; 完全關閉 SDK
  (lifecycle/stop-sdk!)

  ;;
  )
