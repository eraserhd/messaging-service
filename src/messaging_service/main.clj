(ns messaging-service.main
 (:require
  [messaging-service.handler :as handler]
  [ring.adapter.jetty :as jetty]))

(defn -main []
  (let [db-spec (read-string (System/getenv "DB_SPEC"))
        handler (handler/make-handler {:db-spec db-spec})]
    (jetty/run-jetty handler {:port 8080, :join? true})))
