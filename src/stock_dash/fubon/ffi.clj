(ns stock-dash.fubon.ffi
  "低階 FFI bindings，直接封裝 Panama FFI 呼叫"
  (:import [java.lang.foreign Arena MemorySegment ValueLayout]
           [com.fubon.ffi fubon_c_h FubonAccount FubonAccountArray FubonLoginResult FubonBankRemain FubonBankRemainResult]))

;; === Arena 管理 ===
(defonce ^:private global-arena (atom nil))

(defn- ensure-arena []
  (when-not @global-arena
    (reset! global-arena (Arena/ofShared))))

(defn close-arena!
  "關閉並釋放 Arena（僅在應用程式關閉時呼叫）

   警告：
   - 此操作會釋放所有透過 Arena 分配的記憶體
   - 呼叫後無法再使用 FFI（無法分配新記憶體）
   - 只應在 stop-sdk! 中呼叫"
  []
  (when-let [arena @global-arena]
    (try
      (.close arena)
      (reset! global-arena nil)
      (catch Exception e
        (throw (ex-info "關閉 Arena 失敗"
                        {:error e}))))))

(defn arena-initialized?
  "檢查 Arena 是否已初始化"
  []
  (some? @global-arena))

;; === SDK 生命週期 ===
(defn sdk-new
  "建立新的 SDK handle
   回傳：MemorySegment (SDK handle)"
  []
  (ensure-arena)
  (fubon_c_h/fubon_sdk_new))

(defn sdk-free
  "釋放 SDK handle"
  [sdk-handle]
  (when sdk-handle
    (fubon_c_h/fubon_sdk_free sdk-handle)))

;; === 輔助函數 ===
(defn- parse-c-string
  "從 MemorySegment 讀取 C 字串（處理 NULL 指標）"
  [ptr]
  (if (.equals ptr (MemorySegment/NULL))
    nil
    (.getString ptr 0)))

(defn- parse-account-array
  "解析 FubonAccountArray 為 Clojure vector
   參數：accounts-ptr - FubonAccountArray* 指標
   回傳：[{:name str :branch-no str :account str :account-type str} ...]"
  [accounts-ptr]
  (if (.equals accounts-ptr (MemorySegment/NULL))
    []
    (let [count (FubonAccountArray/count accounts-ptr)
          items-ptr (FubonAccountArray/items accounts-ptr)]
      (if (or (zero? count) (.equals items-ptr (MemorySegment/NULL)))
        []
        ;; 遍歷 FubonAccount 陣列
        (mapv (fn [i]
                (let [account-seg (FubonAccount/asSlice items-ptr i)
                      name-ptr (FubonAccount/name account-seg)
                      branch-no-ptr (FubonAccount/branch_no account-seg)
                      account-ptr (FubonAccount/account account-seg)
                      account-type-ptr (FubonAccount/account_type account-seg)]
                  {:name (parse-c-string name-ptr)
                   :branch-no (parse-c-string branch-no-ptr)
                   :account (parse-c-string account-ptr)
                   :account-type (parse-c-string account-type-ptr)}))
              (range count))))))

;; === 登入 ===
(defn login
  "登入富邦證券帳戶
   參數：sdk-handle, personal-id, password, cert-path, cert-pass
   回傳：{:success boolean :accounts [...]} 或 {:success false :error-msg str}"
  [sdk-handle personal-id password cert-path cert-pass]
  (let [arena @global-arena
        ;; 分配 C 字串
        id-str (.allocateFrom arena personal-id)
        pwd-str (.allocateFrom arena password)
        cert-str (.allocateFrom arena cert-path)
        cert-pwd-str (.allocateFrom arena (or cert-pass ""))
        ;; 呼叫 C 函數
        result-seg (fubon_c_h/fubon_login sdk-handle id-str pwd-str cert-str cert-pwd-str)]
    ;; 檢查結果
    (if (.equals result-seg (MemorySegment/NULL))
      {:success false
       :error-msg "登入失敗：回傳 NULL"}
      ;; 解析 FubonLoginResult
      (let [is-success (FubonLoginResult/is_success result-seg)
            error-msg-ptr (FubonLoginResult/error_message result-seg)
            accounts-ptr (FubonLoginResult/accounts result-seg)]
        (if is-success
          ;; 成功：解析 accounts 陣列
          (let [accounts (parse-account-array accounts-ptr)]
            ;; 釋放 C 分配的記憶體
            (fubon_c_h/fubon_free_login_result result-seg)
            {:success true :accounts accounts})
          ;; 失敗
          (let [error-msg (when-not (.equals error-msg-ptr (MemorySegment/NULL))
                            (.getString error-msg-ptr 0))]
            (fubon_c_h/fubon_free_login_result result-seg)
            {:success false :error-msg (or error-msg "Unknown error")}))))))

;; === 帳戶餘額 ===
(defn bank-remain
  "查詢銀行餘額
   參數：sdk-handle, account {:name str :branch-no str :account str :account-type str}
   回傳：{:success boolean :balance long :available-balance long} 或 {:success false :error-msg str}"
  [sdk-handle account]
  (let [arena @global-arena
        ;; 建立 FubonAccount 結構
        account-seg (.allocate arena (FubonAccount/layout))
        ;; 設定 FubonAccount 欄位
        name-str (.allocateFrom arena (or (:name account) ""))
        branch-str (.allocateFrom arena (:branch-no account))
        account-str (.allocateFrom arena (:account account))
        type-str (.allocateFrom arena (or (:account-type account) ""))]
    ;; 寫入結構
    (FubonAccount/name account-seg name-str)
    (FubonAccount/branch_no account-seg branch-str)
    (FubonAccount/account account-seg account-str)
    (FubonAccount/account_type account-seg type-str)
    ;; 呼叫 C 函數
    (let [result-seg (fubon_c_h/fubon_bank_remain sdk-handle account-seg)]
      ;; 檢查結果
      (if (.equals result-seg (MemorySegment/NULL))
        {:success false :error-msg "查詢失敗：回傳 NULL"}
        (let [is-success (FubonBankRemainResult/is_success result-seg)
              error-msg-ptr (FubonBankRemainResult/error_message result-seg)
              data-ptr (FubonBankRemainResult/data result-seg)]
          (if is-success
            ;; 成功：解析 FubonBankRemain
            (let [balance (FubonBankRemain/balance data-ptr)
                  available (FubonBankRemain/available_balance data-ptr)]
              (fubon_c_h/fubon_free_bank_remain_result result-seg)
              {:success true
               :balance balance
               :available-balance available})
            ;; 失敗
            (let [error-msg (when-not (.equals error-msg-ptr (MemorySegment/NULL))
                              (.getString error-msg-ptr 0))]
              (fubon_c_h/fubon_free_bank_remain_result result-seg)
              {:success false :error-msg (or error-msg "Unknown error")})))))))

;; === WebSocket Callback 註冊（TODO）===
;; 以下函數需要等 C SDK 提供 WebSocket callback API 後實作

(defn register-ws-callback
  "註冊 WebSocket callback

   參數：
     sdk-handle  - SDK handle
     account-id  - 帳號 ID（Clojure string）

   功能：
   - 註冊 C callback function
   - C callback 會在收到 WebSocket 訊息時呼叫
   - Callback 應該呼叫 stock-dash.fubon.websocket/ws-callback-handler

   TODO: 實作步驟
   1. 確認 C SDK 的 callback 註冊 API
   2. 將 account-id 轉換為 C string
   3. 建立 callback function pointer
   4. 呼叫 C API 註冊 callback

   範例（實際 API 可能不同）：
   (let [arena @global-arena
         account-id-str (.allocateFrom arena account-id)
         callback-ptr (...)]
     (fubon_c_h/fubon_register_ws_callback
       sdk-handle
       account-id-str
       callback-ptr))"
  [sdk-handle account-id]
  ;; TODO: 實作
  (throw (ex-info "WebSocket callback registration not implemented yet"
                  {:sdk-handle sdk-handle
                   :account-id account-id})))

(defn unregister-ws-callback
  "反註冊 WebSocket callback

   參數：
     sdk-handle  - SDK handle
     account-id  - 帳號 ID

   TODO: 根據 C SDK API 實作"
  [sdk-handle account-id]
  ;; TODO: 實作
  (throw (ex-info "WebSocket callback unregistration not implemented yet"
                  {:sdk-handle sdk-handle
                   :account-id account-id})))

;; === 輔助函數 ===
;; TODO: 實作 parse-account-array, create-account-segment 等