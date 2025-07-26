(ns messaging-service.handler
  (:require
   [clojure.string :as str]
   [next.jdbc :as jdbc]
   [messaging-service.db :as db]))

(defn wrap-handle-messages [next]
  (fn [{:keys [uri] :as request}]
    (if-not (str/starts-with? uri "/api/messages/")
      (next request)
      {:status 200})))

(defn make-handler [{:keys [db-spec]}]
  (let [ds (jdbc/get-datasource db-spec)]
    (db/initialize-and-migrate ds)
    
    (-> (constantly {:status 404, :body "NOT FOUND"})
        (wrap-handle-messages))))
