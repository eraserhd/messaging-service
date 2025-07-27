(ns messaging-service.provider
 (:require
  [clojure.core.async :as async]
  [messaging-service.message :as message]))

(defmulti send-message
  "Sends a message by routing it to a provider who has registered to handle messages
  of this type.

  Provider implementations should return one of:

    {:status :ok},
    {:status :error, :error <msg>}
    {:status :needs-retry, :retry-after <msecs>}
  "
  ::message/type)

(defn- spawn-channel-processor [ch]
  ;; Start a go routine to process messages for this processor.  Better
  ;; things can be implemented, including using a pool of workers, but
  ;; that gets a bit complicated to implement for this exercise.  Requests
  ;; are serialized here so we don't cause a stampede when the service needs
  ;; us to back off.
  (async/go-loop [{:keys [message reply-channel], :as work-item} (async/<! ch)]
    (let [{:keys [status retry-after], :as result} (async/<! (async/io-thread (send-message message)))]
      (if (= status :needs-retry)
        (do
          (async/<! (async/timeout retry-after))
          (recur work-item))
        (do
          (async/>! reply-channel result)
          (when-let [next-item (async/<! ch)]
            (recur next-item)))))))

(defn start
  "Start workers for each registered processor type."
  []
  (reduce
   (fn [providers type]
     (let [queue-ch (async/chan)
           close-ch (spawn-channel-processor queue-ch)]
       (assoc providers type {:queue-ch queue-ch,
                              :close-ch close-ch})))
   {}
   (keys (methods send-message))))

(defn shutdown [processors]
  (doseq [{:keys [queue-ch]} processors]
    (async/close! queue-ch))
  (doseq [{:keys [close-ch]} processors]
    (async/<!! close-ch)))

(defn send-message-with-retries
  "Sends a message like send-message, except that retries are handled automatically.

  Each message type gets its own work queue, and retries suspend that queue's
  processing until the requested timeout expires."
  [processors {:keys [::message/type], :as message}]
  (let [send-channel  (get-in processors [type :queue-ch])
        reply-channel (async/chan)
        _             (async/>!! send-channel {:message message, :reply-channel reply-channel})
        result        (async/alts!! [reply-channel (async/timeout 30000)])]
    (when-not result
      (throw (ex-info "Timeout waiting for processor result." {:type type})))
    result))

