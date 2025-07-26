(ns messaging-service.handler-test
 (:require
  [cheshire.core :as json]
  [clojure.test :refer [deftest is]]
  [clojure.java.io :as io]
  [messaging-service.handler :as handler]
  [ring.mock.request :as mock]))

(def ^:private db-spec {:dbtype "postgres", :dbname "messaging_service", :user "messaging_user", :password "messaging_password"})

(defn- invoke [uri body]
  (let [handler  (handler/make-handler
                  {:db-spec db-spec})
        response (-> (mock/request :post uri)
                     (mock/json-body body)
                     handler
                     (update-in [:body] json/parse-string true))]
    {:response response}))

(deftest t-sms-send
  (let [{:keys [response]} (invoke "/api/messages/sms" {})]
    (is (= 200 (:status response)))
    (is (= "ok" (get-in response [:body :status])))))
