(ns messaging-service.provider.sendgrid
 (:require
  [messaging-service.provider]))

(defmethod messaging-service.provider/send-message :email
  [message]
  (prn "Sendgrid provider recieved this message: " message)
  {:status :ok})
