(ns messaging-service.provider.twilio
  (:require
   [messaging-service.provider]))

(defmethod messaging-service.provider/send-message :sms
  [message]
  (prn "Twilio provider recieved this SMS message: " message)
  {:status :ok})

(defmethod messaging-service.provider/send-message :mms
  [message]
  (prn "Twilio provider recieved this MMS message: " message)
  {:status :ok})
