(ns messaging-service.provider.twilio)

(defmethod messaging-service.provider/send-message :sms
  [message]
  (prn "Twilio provider recieved this SMS message: " message))

(defmethod messaging-service.provider/send-message :mms
  [message]
  (prn "Twilio provider recieved this MMS message: " message))
