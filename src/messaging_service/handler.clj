(ns messaging-service.handler
  (:require
   [clojure.string :as str]
   [next.jdbc :as jdbc]
   [messaging-service.db :as db]))

(defn make-handler [{:keys [db-spec]}]
  (let [ds (jdbc/get-datasource db-spec)]
    (db/initialize-and-migrate ds)
    (fn *handler [{:keys [uri] :as request}]
      (condp #(str/starts-with? %2 %1) uri
       "/api/messages/" {:status 200}
       "/api/webhooks/" {:status 200}

       "/api/conversations/"  {:status 200}
       ;/api/conversations/1/messages {:status 200}

       {:status 404,
        :body "NOT FOUND"}))))
