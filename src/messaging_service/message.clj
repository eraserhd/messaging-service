(ns messaging-service.message
  "This name service is for validation and normalization of messages."
  (:require
   [clojure.set :as set]
   [clojure.string :as str]))

(defn- normalize-address
  "Normalize URLs for lookup and comparison.

  Ensures tel: or mailto: prefix, removes decorative characters, and
  normalizes domain name case.

  I didn't read the entire RFC on tel:, or review the email RFC, so I'm
  sure there's cases missing."
  {:test #(do
           (assert (= "tel:+12016661234" (normalize-address "+1 (201) 666-1234")))
           (assert (= "tel:+12016661234" (normalize-address "tel:+1 (201) 666-1234")))
           (assert (= "mailto:Fo9@bar.com" (normalize-address "Fo9@BaR.COm")))
           (assert (= "mailto:Fo9@bar.com" (normalize-address "mailto:Fo9@BaR.COm"))))}
  [url]
  (condp re-matches url
    #"^(?:tel:)?(\+[-() 0-9]+)$" :>> (fn [[_ number]]
                                       (str "tel:" (str/replace number #"[-() ]" "")))
    #"^(?:mailto:)?(.*)@(.*)$"   :>> (fn [[_ mailbox host]]
                                       (str "mailto:" mailbox "@" (str/lower-case host)))
    (throw (ex-info "Unsure how to interpret address" {:address url}))))

(defn normalize
  "Normalizes a message map."
  [message]
  (-> message
      (set/rename-keys {:type ::type
                        :from ::from
                        :body ::body
                        :timestamp ::timestamp
                        :attachments ::attachments})
      (update ::type keyword)
      (update ::from normalize-address)
      (update ::to   (fn [to]
                       (if (string? to)
                         [to]
                         to)))))
