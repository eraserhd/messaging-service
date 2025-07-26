(ns messaging-service.handler
  (:require
   [clojure.string :as str]
   [next.jdbc :as jdbc]
   [messaging-service.db :as db]
   [ring.middleware.json :as ring-json]))

(defn wrap-handle-messages [next]
  (fn [{:keys [uri data-source body] :as request}]
    (if-not (str/starts-with? uri "/api/messages/")
      (next request)
      (let [message-result (db/insert-message data-source body)]
        {:status 200, :body {:status :ok,
                             :message message-result}}))))

(defn wrap-add-datasource [next ds]
  (fn [request]
    (next (assoc request :data-source ds))))

(defn make-handler [{:keys [db-spec]}]
  (let [data-source (jdbc/get-datasource db-spec)]
    (db/initialize-and-migrate data-source)

    (-> (constantly {:status 404, :body "NOT FOUND"})
        wrap-handle-messages
        ring-json/wrap-json-response
        (ring-json/wrap-json-body {:keywords? true})
        (wrap-add-datasource data-source))))
