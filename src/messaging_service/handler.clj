(ns messaging-service.handler
  (:require
   [next.jdbc :as jdbc]))

(defn make-handler [{:keys [db-spec]}]
  (let [ds (jdbc/get-datasource db-spec)]
    (fn handler [req]
      {:status 404,
       :body "NOT FOUND"})))
