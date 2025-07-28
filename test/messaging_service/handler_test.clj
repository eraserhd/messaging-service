(ns messaging-service.handler-test
 (:require
  [cheshire.core :as json]
  [clojure.test :refer [deftest testing is]]
  [clojure.java.io :as io]
  [messaging-service.handler :as handler]
  [messaging-service.message :as message]
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
    DELETE FROM message_recipients;
    DELETE FROM messages;
    DELETE FROM participant_addresses;
    DELETE FROM participants;
  ")

(defn- invoke
  "Invoke the system-under-test.

  Cleans the database, invokes, queries for resulting objects and returns the objects
  and the API response for interrogation."
  [& args]
  (let [data-source (jdbc/get-datasource db-spec)
        handler     (handler/make-handler {:db-spec db-spec})
        _           (jdbc/execute! data-source [test-db-clean])
        responses   (->> args
                         (partition 2)
                         (map (fn [[uri body]]
                                (let [method (if body :post :get)]
                                  (cond-> (mock/request method uri)
                                    (= method :post) (mock/json-body body)
                                    true             handler
                                    true             (update-in [:body] json/parse-string true)))))
                         (into []))
        messages    (jdbc/execute! data-source ["SELECT * FROM messages;"])
        recipients  (jdbc/execute! data-source ["SELECT \"to\" FROM message_recipients;"])
        attachments (jdbc/execute! data-source ["SELECT * FROM message_attachments;"])]
    {:responses responses
     :messages messages
     :message-attachments attachments
     :message-recipients recipients}))

(defmulti erroring-send-message ::message/type)
(defmethod erroring-send-message :email
  [message]
  {:status :error, :error "U FAIL IT"})

(def ^:private throttled? (atom false))
(defmulti throttled-send-message ::message/type)
(defmethod throttled-send-message :email
  [message]
  (if-not @throttled?
    (do
      (reset! throttled? true)
      {:status :needs-retry, :retry-after 250})
    {:status :ok}))

(deftest t-api-message-endpoints
  (testing "Sending SMS messages"
    (let [{:keys [message-recipients],
           [response] :responses,
           [message] :messages}
          (invoke "/api/messages/sms"
                  {:type "sms",
                   :from "+12016661234",
                   :to "+18045551234",
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
              :messages/timestamp #inst "2025-07-26T03:30:29Z"
              :messages/provider_id nil}
             (dissoc message :messages/id))
          "the stored message has other expected field values")
      (is (= [{:message_recipients/to "tel:+18045551234"}] message-recipients))
      (is (get-in response [:body :message]) "the resulting message was returned in the response")
      (is (= {:type "sms"
              :from "tel:+12016661234"
              :body "helloo!!"
              :timestamp "2025-07-26T03:30:29Z"
              :provider_id nil}
             (-> response :body :message (dissoc :id)))
          "message fields were correctly returned in response")
      (is (= (get-in response [:body :message :id])
             (str (:messages/id message)))
          "the correct id was returned")))
  (testing "Sending MMS messages, with attachments"
    (let [{:keys [message-attachments],
           [response] :responses
           [message] :messages}
          (invoke "/api/messages/sms"
                  {:type "mms",
                   :from "+12016661234",
                   :to "+18045551234",
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
    (let [{:keys [message-attachments message-recipients],
           [response] :responses
           [message] :messages}
          (invoke "/api/messages/email"
                  {:from "user@usehatchapp.com",
                   :to "contact@gmail.com",
                   :body "Hello! This is a test email message with <b>HTML</b> formatting.",
                   :attachments ["https://example.com/document.pdf"],
                   :timestamp "2024-11-01T14:00:00Z"})]
      (is (= 200 (:status response)))
      (is (= "ok" (get-in response [:body :status])))
      (is (= "email" (:messages/type message))
          "it has the right type")
      (is (= [{:message_recipients/to "mailto:contact@gmail.com"}] message-recipients))
      (is (= #{"https://example.com/document.pdf"}
             (->> message-attachments
                  (map :message_attachments/url)
                  (into #{}))))))
  (testing "When the downstream provider fails"
    (with-redefs [provider/send-message erroring-send-message]
      (let [{:keys [message-attachments],
             [response] :responses
             [message] :messages}
            (invoke "/api/messages/email"
                    {:from "user@usehatchapp.com",
                     :to "contact@gmail.com",
                     :body "Hello! This is a test email message with <b>HTML</b> formatting.",
                     :attachments ["https://example.com/document.pdf"],
                     :timestamp "2024-11-01T14:00:00Z"})]
        (is (= 500 (:status response)))
        (is (= "error" (get-in response [:body :status]))))))
  (testing "When the downstream provider wants us to back off"
    (with-redefs [provider/send-message throttled-send-message]
      (reset! throttled? false)
      (let [start                         (System/currentTimeMillis)
            {:keys [message-attachments],
             [response] :responses,
             [message] :messages}         (invoke "/api/messages/email"
                                            {:from "user@usehatchapp.com",
                                             :to "contact@gmail.com",
                                             :body "Hello! This is a test email message with <b>HTML</b> formatting.",
                                             :attachments ["https://example.com/document.pdf"],
                                             :timestamp "2024-11-01T14:00:00Z"})
            end                           (System/currentTimeMillis)]
        (is (= 200 (:status response)))
        (is (= "ok" (get-in response [:body :status])))
        (is (<= 250 (- end start))
            "it should have waited at least 250 msec")))))

(deftest t-api-webhook-endpoints
  (testing "Receiving email messages"
    (let [{:keys [message-recipients]
           [response] :responses
           [message] :messages}
          (invoke "/api/webhooks/email"
                  {:from "contact@gmail.com",
                   :to "user@usehatchapp.com",
                   :xillio_id "message-3",
                   :body "<html><body>This is an incoming email with <b>HTML</b> content</body></html>",
                   :attachments ["https://example.com/received-document.pdf"],
                   :timestamp "2024-11-01T14:00:00Z"})]
      (is (= 200 (:status response)))
      (is (= "ok" (get-in response [:body :status])))
      (is (= "email" (:messages/type message)))
      (is (= "message-3" (:messages/provider_id message)))
      (is (= [{:message_recipients/to "mailto:user@usehatchapp.com"}] message-recipients))))
  (testing "Receiving SMS messages"
    (let [{:keys [message-recipients]
           [response] :responses
           [message] :messages}
          (invoke "/api/webhooks/sms"
                  {:from "+18045551234",
                   :to "+12016661234",
                   :type "sms",
                   :messaging_provider_id "message-1",
                   :body "This is an incoming SMS message",
                   :attachments nil,
                   :timestamp "2024-11-01T14:00:00Z"})]
      (is (= 200 (:status response)))
      (is (= "ok" (get-in response [:body :status])))
      (is (= "sms" (:messages/type message)))
      (is (= "message-1" (:messages/provider_id message)))
      (is (= [{:message_recipients/to "tel:+12016661234"}] message-recipients))))
  (testing "Receiving MMS messages"
    (let [{:keys [message-attachments]
           [response] :responses
           [message] :messages}
          (invoke "/api/webhooks/sms"
                  {:from "+18045551234",
                   :to "+12016661234",
                   :type "mms",
                   :messaging_provider_id "message-1",
                   :body "This is an incoming SMS message",
                   :attachments ["https://example.com/received-image.jpg"],
                   :timestamp "2024-11-01T14:00:00Z"})]
      (is (= 200 (:status response)))
      (is (= "ok" (get-in response [:body :status])))
      (is (= "mms" (:messages/type message)))
      (is (= #{"https://example.com/received-image.jpg"}
             (->> message-attachments
                  (map :message_attachments/url)
                  (into #{})))
          "the attachments were stored in the database"))))

(deftest t-api-conversations
  (testing "Conversations list endpoint"
    (let [{:keys [message-recipients]
           [_ response] :responses
           [message] :messages}
          (invoke
           "/api/webhooks/sms"
           {:from "+18045551234",
            :to "+12016661234",
            :type "mms",
            :messaging_provider_id "message-1",
            :body "This is an incoming SMS message",
            :attachments ["https://example.com/received-image.jpg"],
            :timestamp "2024-11-01T14:00:00Z"}
           "/api/conversations"
           nil)]
      (is (= 200 (:status response)))
      (is (= "ok" (get-in response [:body :status])))
      (is (= 1 (count (get-in response [:body :conversations])))))))
