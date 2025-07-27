(ns messaging-service.provider.twilio
  (:require
   [messaging-service.message :as message]
   [messaging-service.provider :as provider]))

(defmethod provider/send-message :sms
  [message]
  (prn "Twilio provider received this SMS message: " message)
  {:status :ok})

(defmethod provider/send-message :mms
  [message]
  (prn "Twilio provider received this MMS message: " message)
  {:status :ok})

(defmethod provider/extract-webhook-message :sms
  [_ body]
  (let [{:keys [from
                to
                type
                messaging_provider_id
                body
                attachments
                timestamp]} body]
    (message/normalize
     {::message/from        from
      ::message/to          to
      ::message/type        type
      ::message/provider_id messaging_provider_id
      ::message/body        body
      ::message/attachments attachments
      ::message/timestamp   timestamp})))
