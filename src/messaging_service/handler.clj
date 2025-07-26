(ns messaging-service.handler
  (:require
   [next.jdbc :as jdbc]))

(defn make-handler [{:keys [db-spec]}]
  (let [ds (jdbc/get-datasource db-spec)]
    (fn handler [{:keys [uri] :as request}]
      (case uri

       "/api/messages/sms"
       {:status 200}

       {:status 404,
        :body "NOT FOUND"}))))
