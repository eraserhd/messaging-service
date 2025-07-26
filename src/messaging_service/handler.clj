(ns messaging-service.handler
  (:require
   [clojure.string :as str]
   [next.jdbc :as jdbc]
   [messaging-service.db :as db]
   [ring.middleware.json :as ring-json]))

(defn wrap-handle-messages [next]
  (fn [{:keys [uri] :as request}]
    (if-not (str/starts-with? uri "/api/messages/")
      (next request)
      {:status 200, :body {:status :ok}})))

(defn make-handler [{:keys [db-spec]}]
  (let [ds (jdbc/get-datasource db-spec)]
    (db/initialize-and-migrate ds)

    (-> (constantly {:status 404, :body "NOT FOUND"})
        wrap-handle-messages
        ring-json/wrap-json-response)))
