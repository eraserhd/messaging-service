(ns messaging-service.handler-test
 (:require
  [cheshire.core :as json]
  [clojure.test :refer [deftest is]]
  [clojure.java.io :as io]
  [messaging-service.handler :as handler]
  [next.jdbc :as jdbc]
  [ring.mock.request :as mock]))

(def ^:private db-spec
  {:dbtype "postgres", :dbname "messaging_service", :user "messaging_user", :password "messaging_password"})

(def ^:private test-db-clean
  "
    DELETE FROM messages;
    DELETE FROM participant_addresses;
    DELETE FROM participants;
  ")

(defn- invoke [uri body]
  (let [data-source (jdbc/get-datasource db-spec)
        _           (jdbc/execute! data-source [test-db-clean])
        handler     (handler/make-handler
                     {:db-spec db-spec})
        response    (-> (mock/request :post uri)
                        (mock/json-body body)
                        handler
                        (update-in [:body] json/parse-string true))
        messages    (jdbc/execute! data-source ["SELECT * FROM messages;"])]
    {:response response
     :messages messages}))

(deftest t-sms-send
  (let [{:keys [response], [message] :messages} (invoke "/api/messages/sms" {:type "sms",
                                                                             :from "mailto:jason.m.felice@gmail.com",
                                                                             :body "helloo!!"
                                                                             :timestamp "2025-07-26T03:30:29Z"})]
    (is (= 200 (:status response)))
    (is (= "ok" (get-in response [:body :status])))
    (is message "the message was stored in the database.")
    (is (:messages/id message) "the stored message has a unique id")
    (is (= {:messages/type "sms"
            :messages/from "mailto:jason.m.felice@gmail.com"
            :messages/body "helloo!!"
            :messages/timestamp #inst "2025-07-26T03:30:29Z"}
           (dissoc message :messages/id))
        "the stored message has other expected field values")

    (is (get-in response [:body :message]) "the resulting message was returned in the response")
    (is (= {:type "sms"
            :from "mailto:jason.m.felice@gmail.com"
            :body "helloo!!"
            :timestamp "2025-07-26T03:30:29Z"}
           (-> response :body :message (dissoc :id)))
        "message fields were correctly returned in response")
    (is (= (get-in response [:body :message :id])
           (str (:messages/id message)))
        "the correct id was returned")))
    ;; Recipients were added .
