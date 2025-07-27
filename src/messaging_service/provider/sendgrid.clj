(ns messaging-service.provider.sendgrid
 (:require
  [messaging-service.message :as message]
  [messaging-service.provider :as provider]))

(defmethod provider/send-message :email
  [message]
  (prn "Sendgrid provider received this message: " message)
  {:status :ok})

(defmethod provider/extract-webhook-message :email
  [_ body]
  (let [{:keys [from to xillio_id body attachments timestamp]} body]
    (message/normalize
     {::message/type        :email,
      ::message/from        from,
      ::message/to          to,
      ::message/provider_id xillio_id,
      ::message/body        body,
      ::message/attachments attachments,
      ::message/timestamp   timestamp})))
