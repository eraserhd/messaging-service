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

(def ^:private provider-processing-channels (atom {}))

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

(defn- provider-processing-channel [type]
  (when-not (contains? (methods send-message) type)
    (throw (ex-info (str "Unknown type " type) {:type type})))
  (let [[old new] (swap-vals! provider-processing-channels
                              (fn [old]
                                (if (contains? old type)
                                  old
                                  (assoc old type (async/chan 100)))))
        ch        (get new type)]
    (when-not (= old new)
      (spawn-channel-processor ch))
    ch))

(defn send-message-with-retries
  "Sends a message like send-message, except that retries are handled automatically.

  Each message type gets its own work queue, and retries suspend that queue's
  processing until the requested timeout expires."
  [{:keys [::message/type], :as message}]
  (let [send-channel  (provider-processing-channel type)
        reply-channel (async/chan)
        _             (async/>!! send-channel {:message message, :reply-channel reply-channel})
        result        (async/alts!! [reply-channel (async/timeout 30000)])]
    (when-not result
      (throw (ex-info "Timeout waiting for processor result." {:type type})))
    result))

(defn shutdown []
  (let [old (reset! provider-processing-channels {})]
    (doseq [channel (vals old)]
      (async/close! channel))))
