(ns messaging-service.handler-test
 (:require
  [cheshire.core :as json]
  [clojure.test :refer [deftest testing is]]
  [clojure.java.io :as io]
  [messaging-service.handler :as handler]
  [messaging-service.provider :as provider]
  [next.jdbc :as jdbc]
  [ring.mock.request :as mock]

  ;; Register providers
  [messaging-service.provider.sendgrid]
  [messaging-service.provider.twilio]))

(def ^:private db-spec
  {:dbtype "postgres", :dbname "messaging_service", :user "messaging_user", :password "messaging_password"})

(def ^:private test-db-clean
  "
    DELETE FROM message_attachments;
    DELETE FROM messages;
    DELETE FROM participant_addresses;
    DELETE FROM participants;
  ")

(defn- invoke
  "Invoke the system-under-test.

  Cleans the database, invokes, queries for resulting objects and returns the objects
  and the API response for interrogation."
  [uri body]
  (provider/shutdown)
  (let [data-source (jdbc/get-datasource db-spec)
        _           (jdbc/execute! data-source [test-db-clean])
        handler     (handler/make-handler
                     {:db-spec db-spec})
        response    (-> (mock/request :post uri)
                        (mock/json-body body)
                        handler
                        (update-in [:body] json/parse-string true))
        messages    (jdbc/execute! data-source ["SELECT * FROM messages;"])
        attachments (jdbc/execute! data-source ["SELECT * FROM message_attachments;"])]
    {:response response
     :messages messages
     :message-attachments attachments}))

(deftest t-api-message-endpoints
  (testing "Sending SMS messages"
    (let [{:keys [response],
           [message] :messages}
          (invoke "/api/messages/sms"
                  {:type "sms",
                   :from "+12016661234",
                   :body "helloo!!"
                   :timestamp "2025-07-26T03:30:29Z"
                   :attachments nil})]
      (is (= 200 (:status response)))
      (is (= "ok" (get-in response [:body :status])))
      (is message "the message was stored in the database.")
      (is (:messages/id message) "the stored message has a unique id")
      (is (= {:messages/type "sms"
              :messages/from "tel:+12016661234"
              :messages/body "helloo!!"
              :messages/timestamp #inst "2025-07-26T03:30:29Z"}
             (dissoc message :messages/id))
          "the stored message has other expected field values")

      (is (get-in response [:body :message]) "the resulting message was returned in the response")
      (is (= {:type "sms"
              :from "tel:+12016661234"
              :body "helloo!!"
              :timestamp "2025-07-26T03:30:29Z"}
             (-> response :body :message (dissoc :id)))
          "message fields were correctly returned in response")
      (is (= (get-in response [:body :message :id])
             (str (:messages/id message)))
          "the correct id was returned")))
  (testing "Sending MMS messages, with attachments"
    (let [{:keys [response message-attachments],
           [message] :messages}
          (invoke "/api/messages/sms"
                  {:type "mms",
                   :from "+12016661234",
                   :body "helloo!!"
                   :timestamp "2025-07-26T03:30:29Z"
                   :attachments ["https://example.com/image.jpg"
                                 "https://example.com/surprise_pikachu.jpg"]})]
      (is (= 200 (:status response)))
      (is (= "ok" (get-in response [:body :status])))
      (is (= "mms" (:messages/type message))
          "it has the right type")
      (is (= #{"https://example.com/image.jpg"
               "https://example.com/surprise_pikachu.jpg"}
             (->> message-attachments
                  (map :message_attachments/url)
                  (into #{})))
          "the attachments were stored in the database")))
  (testing "Sending email messages"
    (let [{:keys [response message-attachments],
           [message] :messages}
          (invoke "/api/messages/email"
                  {:from "+12016661234",
                   :body "helloo!!"
                   :timestamp "2025-07-26T03:30:29Z"
                   :attachments ["https://example.com/image.jpg"
                                 "https://example.com/surprise_pikachu.jpg"]})]
      (is (= 200 (:status response)))
      (is (= "ok" (get-in response [:body :status])))
      (is (= "email" (:messages/type message))
          "it has the right type")
      (is (= #{"https://example.com/image.jpg"
               "https://example.com/surprise_pikachu.jpg"}
             (->> message-attachments
                  (map :message_attachments/url)
                  (into #{})))))))

    ;; Recipients were added .
