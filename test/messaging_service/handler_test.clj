(ns messaging-service.handler-test
 (:require
  [cheshire.core :as json]
  [clojure.test :refer [deftest is]]
  [clojure.java.io :as io]
  [messaging-service.handler :as handler]
  [ring.mock.request :as mock]))

(defn- test-handler [& override]
  (handler/make-handler
   (merge
     {:db-spec {:dbtype "postgres", :dbname "messaging_service", :user "messaging_user", :password "messaging_password"}}
     override)))

(deftest t-sms-send
  (let [handler  (test-handler)
        {:keys [status body]} (-> (mock/request :post "/api/messages/sms")
                                  (mock/json-body {})
                                  handler)
        parsed-body (json/parse-string body true)]
    (is (= 200 status))
    (is (= "ok" (get parsed-body :status)))))
