(ns messaging-service.handler-test
 (:require
  [clojure.test :refer [deftest is]]
  [messaging-service.handler :as handler]
  [ring.mock.request :as mock]))

(defn- test-handler [& override]
  (handler/make-handler
   (merge
     {:db-spec {:dbtype "postgres", :dbname "messaging_service", :user "messaging_user", :password "messaging_password"}}
     override)))

(deftest t-sms-send
  (let [handler  (test-handler)
        {:keys [status]} (-> (mock/request :post "/api/messages/sms")
                             (mock/json-body {})
                             handler)]
    (is (= 200 status))))
