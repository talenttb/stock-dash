(ns stock-dash.fubon.lifecycle
  "Fubon SDK lifecycle management

   架構：
   - SDK handle：全域唯一，使用 delay 延遲初始化，長期存在
   - Account：以 personal-id 為單位管理，每個身份證可有多個帳號
   - Reload 友善：defonce 確保狀態在 reload 時保持不變

   設計原則：完全對齊原始 C++ SDK
   - C++ SDK: login(personal_id) -> vector<Account>
   - Clojure: {personal-id -> {:accounts [...] :channels [...] :consumers [...]}}"
  (:require [stock-dash.fubon.ffi :as ffi]
            [clojure.core.async :as async]
            [com.brunobonacci.mulog :as mu]))

;; === SDK 管理（應用程式級別）===
(defonce ^{:doc "SDK handle，使用 delay 確保只初始化一次。reload 時不會重新初始化"}
  sdk
  (delay
    (mu/log ::sdk-initializing)
    (try
      (let [handle (ffi/sdk-new)]
        (mu/log ::sdk-initialized)
        (println "✓ Fubon SDK initialized")
        handle)
      (catch Exception e
        (mu/log ::sdk-init-failed :exception e)
        (println "✗ Fubon SDK 初始化失敗：" (.getMessage e))
        (throw e)))))

(defn get-sdk
  "取得 SDK handle（自動初始化）

   第一次呼叫時會觸發 delay 初始化，
   之後的呼叫直接回傳已初始化的 handle"
  []
  @sdk)

(defn sdk-initialized?
  "檢查 SDK 是否已初始化"
  []
  (realized? sdk))

;; === Account 管理（以 personal-id 為核心）===
(defonce ^{:doc "多身份證狀態管理
                 格式：{personal-id -> {:accounts   [account-map ...]
                                        :channels   [channel ...]
                                        :consumers  [consumer ...]
                                        :logged-in? boolean}}

                 說明：
                 - 第一層 key：personal-id（身份證字號）
                 - :accounts：該身份證的所有證券帳號（vector）
                 - :channels, :consumers：索引對應 accounts"}
  accounts
  (atom {}))

(defonce ^{:doc "反向索引：從 account 快速找到 personal-id
                 格式：{[branch-no account] -> {:personal-id str
                                                 :account-index int}}"}
  account-index
  (atom {}))

(defn get-accounts
  "取得某 personal-id 的所有帳號

   參數：personal-id - 身份證字號
   回傳：[account-map ...] 或 nil"
  [personal-id]
  (get-in @accounts [personal-id :accounts]))

(defn find-personal-id-by-account
  "從 branch-no + account 快速找到對應的 personal-id 和 account-index

   參數：
     branch-no - 分公司代碼
     account   - 帳號

   回傳：{:personal-id str :account-index int} 或 nil

   範例：
   (find-personal-id-by-account \"1234\" \"567890\")
   ;=> {:personal-id \"A123456789\" :account-index 0}"
  [branch-no account]
  (get @account-index [branch-no account]))

(defn list-personal-ids
  "列出所有已註冊的 personal-id"
  []
  (keys @accounts))

(defn person-logged-in?
  "檢查特定 personal-id 是否已登入"
  [personal-id]
  (get-in @accounts [personal-id :logged-in?] false))

(defn any-logged-in?
  "檢查是否有任何帳號已登入"
  []
  (boolean (some :logged-in? (vals @accounts))))

(defn get-channel
  "取得特定帳號的 channel

   參數：
     personal-id - 身份證字號
     account-index - 帳號索引（對應 accounts vector）"
  [personal-id account-index]
  (get-in @accounts [personal-id :channels account-index]))

(defn set-consumer!
  "設定特定帳號的 consumer control channel

   參數：
     personal-id - 身份證字號
     account-index - 帳號索引
     control-ch - consumer control channel"
  [personal-id account-index control-ch]
  (swap! accounts assoc-in [personal-id :consumers account-index] control-ch))

;; === Channel 管理 ===
(defn- create-account-channel
  "為帳號建立 WebSocket message channel

   使用 sliding-buffer：
   - 當 buffer 滿時，自動丟棄最舊的訊息
   - 防止 memory leak
   - 適合 high-throughput 場景"
  [buffer-size]
  (async/chan (async/sliding-buffer buffer-size)))

(defn register-person!
  "註冊一個 personal-id 及其所有帳號

   參數：
     personal-id - 身份證字號
     accounts-data - 帳號資訊 vector（從 C SDK login 回傳）
     :buffer-size - channel buffer 大小（預設 100）"
  [personal-id accounts-data & {:keys [buffer-size] :or {buffer-size 100}}]
  (when-not (get-accounts personal-id)
    (let [channels (mapv (fn [_] (create-account-channel buffer-size)) accounts-data)
          consumers (vec (repeat (count accounts-data) nil))]
      ;; 註冊到 accounts atom
      (swap! accounts assoc personal-id
             {:accounts accounts-data
              :channels channels
              :consumers consumers
              :logged-in? false})
      ;; 建立反向索引
      (doseq [[idx acc] (map-indexed vector accounts-data)]
        (swap! account-index assoc
               [(:branch-no acc) (:account acc)]
               {:personal-id personal-id
                :account-index idx}))
      (mu/log ::person-registered
              :personal-id personal-id
              :account-count (count accounts-data)))))

(defn set-person-logged-in!
  "設定 personal-id 登入狀態"
  [personal-id status]
  (swap! accounts assoc-in [personal-id :logged-in?] status))

(defn unregister-person!
  "反註冊整個 personal-id（關閉所有 channels 和 consumers）

   清理步驟：
   1. 停止所有 consumers
   2. 關閉所有 channels
   3. 清理反向索引
   4. 從 accounts map 中移除"
  [personal-id]
  (when-let [person-data (get @accounts personal-id)]
    ;; 停止所有 consumers
    (doseq [consumer (:consumers person-data)]
      (when consumer
        (async/close! consumer)))
    ;; 關閉所有 channels
    (doseq [ch (:channels person-data)]
      (when ch
        (async/close! ch)))
    ;; 清理反向索引
    (doseq [acc (:accounts person-data)]
      (swap! account-index dissoc [(:branch-no acc) (:account acc)]))
    ;; 移除記錄
    (swap! accounts dissoc personal-id)
    (mu/log ::person-unregistered :personal-id personal-id)))

;; === Debug 函數 ===
(defn person-stats
  "取得 personal-id 狀態統計（用於偵錯）

   回傳格式：
   {personal-id -> {:logged-in? boolean
                    :account-count int
                    :channels-open [boolean ...]
                    :consumers-running [boolean ...]}}"
  []
  (into {}
        (map (fn [[personal-id data]]
               [personal-id
                {:logged-in? (:logged-in? data)
                 :account-count (count (:accounts data))
                 :channels-open (mapv (comp not nil?) (:channels data))
                 :consumers-running (mapv (comp not nil?) (:consumers data))}]))
        @accounts))

;; === SDK 關閉（必須放在最後，因為依賴所有其他函數）===
(defn stop-sdk!
  "強制關閉 SDK 和所有資源（僅在應用程式關閉時呼叫）

   完整清理流程：
   1. 登出所有 personal-id（清理 Clojure 層狀態）
   2. 釋放 SDK handle（清理 C 層資源）
   3. 關閉 Arena（釋放 FFI 記憶體）

   警告：
   - 此操作會釋放所有 native 資源
   - 呼叫後 SDK 無法再使用
   - 正常開發流程中不應呼叫此函數"
  []
  (when (sdk-initialized?)
    (mu/log ::sdk-stopping)
    (try
      ;; 1. 先登出所有 personal-id（清理 Clojure 層）
      ;; 這會停止所有 consumers、關閉 channels、清理索引
      (doseq [personal-id (list-personal-ids)]
        (unregister-person! personal-id))

      ;; 2. 釋放 SDK handle（清理 C SDK）
      (ffi/sdk-free @sdk)

      ;; 3. 關閉 Arena（釋放 FFI 記憶體）
      (ffi/close-arena!)

      (mu/log ::sdk-stopped)
      (println "✓ Fubon SDK stopped")

      (catch Exception e
        (mu/log ::sdk-stop-failed :exception e)
        (println "✗ Fubon SDK 關閉失敗：" (.getMessage e))
        (throw e)))))
