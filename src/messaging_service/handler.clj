(ns messaging-service.handler
  (:require
   [clojure.string :as str]
   [clojure.walk :as walk]
   [next.jdbc :as jdbc]
   [messaging-service.db :as db]
   [messaging-service.message :as message]
   [messaging-service.provider :as provider]
   [ring.middleware.json :as ring-json]))

(defn- denamespace [m]
  (walk/postwalk
   (fn [x]
     (if (keyword? x)
       (keyword (name x))
       x))
   m))

(defn wrap-handle-messages [next]
  (fn [{:keys [uri data-source body providers] :as request}]
    (if-let [[_ url-type]  (re-matches #"^/api/messages/([^/]*)/?$" uri)]
      (let [message        (message/normalize (merge {:type (keyword url-type)} body))
            send-result    (provider/send-message-with-retries providers message)
            message-result (db/insert-message data-source message)]
        {:status 200,
         :body {:status :ok,
                :message (denamespace message-result)}})
      (next request))))

(defn wrap-handle-webhooks [next]
  (fn [{:keys [uri data-source body], :as request}]
    (if-let [[_ url-type] (re-matches #"^/api/webhooks/([^/]*)/?$" uri)]
      (let [message       (message/normalize (merge {:type (keyword url-type)} body))
            _             (db/insert-message data-source message)]
        {:status 200,
         :body {:status :ok}})
      (next request))))

(defn wrap-add-datasource [next ds]
  (fn [request]
    (next (assoc request :data-source ds))))

(defn wrap-add-providers [next providers]
  (fn [request]
    (next (assoc request :providers providers))))

(defn make-handler [{:keys [db-spec]}]
  (let [data-source (jdbc/get-datasource db-spec)]
    (db/initialize-and-migrate data-source)

    (-> (constantly {:status 404, :body "NOT FOUND"})
        wrap-handle-messages
        wrap-handle-webhooks
        ring-json/wrap-json-response
        (ring-json/wrap-json-body {:keywords? true})
        (wrap-add-providers (provider/start))
        (wrap-add-datasource data-source))))
