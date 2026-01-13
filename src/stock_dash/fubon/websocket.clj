(ns stock-dash.fubon.websocket
  "WebSocket message consumer 和 handler

   架構：
   - Consumer：per-account go-loop，從 channel 讀取訊息並處理
   - Handler：使用 multimethod 做訊息分發，易於擴展
   - C Callback：輕量入口，只做 parse + put，不處理業務邏輯"
  (:require [clojure.core.async :as async]
            [stock-dash.fubon.lifecycle :as lifecycle]
            [com.brunobonacci.mulog :as mu]))

;; === Message Handler（使用 multimethod 做分發）===
(defmulti handle-ws-message
  "根據 message type 分發處理

   msg 格式：{:type keyword :data map}

   擴展方式：
   (defmethod handle-ws-message :new-type [personal-id account-index msg]
     ;; 處理邏輯
     )"
  (fn [personal-id account-index msg] (:type msg)))

;; 預設 handler
(defmethod handle-ws-message :default [personal-id account-index msg]
  (mu/log ::unknown-message-type
          :personal-id personal-id
          :account-index account-index
          :message-type (:type msg)
          :message msg))

;; === 範例 Handlers ===

;; 處理報價
(defmethod handle-ws-message :quote [personal-id account-index {:keys [data]}]
  (mu/log ::quote-received
          :personal-id personal-id
          :account-index account-index
          :symbol (:symbol data)
          :price (:price data)
          :volume (:volume data))
  ;; TODO: 更新報價資料到狀態管理
  ;; 可以整合到 atom 或資料庫
  )

;; 處理成交回報
(defmethod handle-ws-message :trade [personal-id account-index {:keys [data]}]
  (mu/log ::trade-received
          :personal-id personal-id
          :account-index account-index
          :order-id (:order-id data)
          :status (:status data)
          :filled-qty (:filled-qty data))
  ;; TODO: 更新訂單狀態
  )

;; 處理訂單更新
(defmethod handle-ws-message :order-update [personal-id account-index {:keys [data]}]
  (mu/log ::order-update-received
          :personal-id personal-id
          :account-index account-index
          :order-id (:order-id data)
          :status (:status data))
  ;; TODO: 更新訂單狀態
  )

;; 處理錯誤訊息
(defmethod handle-ws-message :error [personal-id account-index {:keys [data]}]
  (mu/log ::error-received
          :personal-id personal-id
          :account-index account-index
          :error-code (:code data)
          :error-message (:message data))
  ;; TODO: 錯誤處理邏輯
  ;; 可能需要通知使用者或重試
  )

;; === Consumer 管理 ===
(defn start-consumer!
  "啟動 account 的 message consumer

   建立 go-loop 持續從 channel 讀取訊息並處理：
   - 使用 async/alt! 監聽 control channel 和 data channel
   - control channel：用來停止 consumer
   - data channel：接收 WebSocket 訊息

   參數：
     personal-id - 身份證字號
     account-index - 帳號索引（對應 accounts vector）

   回傳：control channel（用來停止 consumer）"
  [personal-id account-index]
  (if-let [data-ch (lifecycle/get-channel personal-id account-index)]
    (let [control-ch (async/chan)]
      (async/go-loop []
        (async/alt!
          ;; 收到停止信號
          control-ch ([_]
                      (mu/log ::consumer-stopped
                              :personal-id personal-id
                              :account-index account-index)
                      nil)  ; 退出 loop

          ;; 收到 WebSocket 訊息
          data-ch ([msg]
                   (when msg
                     (try
                       (handle-ws-message personal-id account-index msg)
                       (catch Exception e
                         (mu/log ::consumer-error
                                 :personal-id personal-id
                                 :account-index account-index
                                 :error-message (.getMessage e)
                                 :error-type (type e))))
                     (recur)))))

      ;; 記錄 control channel
      (lifecycle/set-consumer! personal-id account-index control-ch)
      (mu/log ::consumer-started
              :personal-id personal-id
              :account-index account-index)
      control-ch)

    (throw (ex-info "Account channel not found"
                    {:personal-id personal-id
                     :account-index account-index}))))

(defn stop-consumer!
  "停止 account 的 consumer

   關閉 control channel 會觸發 go-loop 退出

   參數：
     personal-id - 身份證字號
     account-index - 帳號索引"
  [personal-id account-index]
  (when-let [control-ch (get-in @lifecycle/accounts [personal-id :consumers account-index])]
    (async/close! control-ch)
    (lifecycle/set-consumer! personal-id account-index nil)))

;; === C Callback 入口 ===
(defn ws-callback-handler
  "從 C callback 呼叫的 Clojure 函數

   設計原則：
   - 極輕量：只做 parse + put
   - 不處理業務邏輯
   - Memory 安全：立即 parse C data 並釋放

   參數：
     personal-id - 身份證字號
     account-index - 帳號索引
     raw-data - C 層傳來的原始資料（MemorySegment 或已 parse 的 map）"
  [personal-id account-index raw-data]
  (when-let [ch (lifecycle/get-channel personal-id account-index)]
    (let [;; TODO: 實際的 parse 邏輯
          ;; 這裡需要根據 C SDK 的資料格式來實作
          ;; 目前使用假資料作為示範
          parsed-msg (if (map? raw-data)
                       raw-data  ; 已經是 map，直接使用
                       ;; 否則需要 parse C struct
                       {:type :quote
                        :data {:symbol "2330"
                               :price 650
                               :volume 1000}})]
      ;; 使用 >!! 而非 >!（因為可能不在 go block 中）
      ;; sliding-buffer 確保不會 block
      (async/>!! ch parsed-msg))))

;; === Debug 輔助函數 ===
(defn simulate-message!
  "模擬 WebSocket 訊息（用於測試）

   可以在 REPL 中使用：
   (simulate-message! personal-id account-index {:type :quote :data {...}})"
  [personal-id account-index msg]
  (ws-callback-handler personal-id account-index msg))
