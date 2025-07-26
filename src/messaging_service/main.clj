(ns messaging-service.main
 (:require
  [messaging-service.handler :as handler]
  [ring.adapter.jetty :as jetty]))

(defn -main []
  (jetty/run-jetty handler/handler {:port 8080, :join? true}))
