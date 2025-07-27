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
  (message/normalize (assoc body ::message/type :email)))
