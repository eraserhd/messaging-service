(ns messaging-service.provider
 (:require
  [messaging-service.message :as message]))

(defmulti send-message
  "Sends a message by routing it to a provider who has registered to handle it."
  ::message/type)

(defn registered-types
  "Returns a list of registered types."
  []
  (into #{} (keys (methods send-message))))
