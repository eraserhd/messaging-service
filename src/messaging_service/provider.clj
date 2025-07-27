(ns messaging-service.provider
 (:require
  [messaging-service.message :as message]))

(defmulti send-message
  "Sends a message by routing it to a provider who has registered to handle it."
  (fn [message]
    (::message/type message)))

(defn supported-types
  "Returns a list of supported types."
  []
  (keys (methods send-message)))
