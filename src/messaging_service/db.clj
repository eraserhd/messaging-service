(ns messaging-service.db
 (:require
  [messaging-service.message :as message]
  [next.jdbc :as jdbc]
  [next.jdbc.date-time]))

(def ^:private init-script
  "
    CREATE TABLE IF NOT EXISTS participants (
      id UUID DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY
    );
    CREATE TABLE IF NOT EXISTS participant_addresses (
      url TEXT NOT NULL PRIMARY KEY,
      participant_id UUID NOT NULL REFERENCES participants ( id )
    );

    CREATE TABLE IF NOT EXISTS message_types (
      id TEXT NOT NULL PRIMARY KEY
    );
    INSERT INTO message_types VALUES
      ( 'sms' ),
      ( 'mms' ),
      ( 'email' )
    ON CONFLICT DO NOTHING;

    CREATE TABLE IF NOT EXISTS messages (
      id UUID DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
      \"from\" TEXT NOT NULL REFERENCES participant_addresses ( url ),
      type TEXT NOT NULL REFERENCES message_types ( id ),
      body TEXT NOT NULL,
      timestamp TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
    );

    CREATE TABLE IF NOT EXISTS message_attachments (
      message_id UUID NOT NULL REFERENCES messages ( id ),
      url TEXT NOT NULL,
      PRIMARY KEY ( message_id, url )
    );

    CREATE TABLE IF NOT EXISTS message_recipients (
      message_id UUID NOT NULL REFERENCES messages ( id ),
      \"to\" TEXT NOT NULL REFERENCES participant_addresses ( url ),
      PRIMARY KEY ( message_id, \"to\" )
    );
  ")

(defn initialize-and-migrate [ds]
  (jdbc/execute! ds [init-script]))

(defn upsert-participant
  "Creates a new address and participant, unless the address already exists.

  Returns the new or existing participant ID."
  [ds url]
  (jdbc/with-transaction [tx ds]
    ;; Advisory lock is necessary to prevent race on two processes attempting to insert, causing
    ;; one transaction to fail.  Normally, I'd do this with INSERT .. ON CONFLICT UPDATE, but
    ;; that's not possible here because of the foreign key and two insert statements.
    ;;
    ;; (hash url) uses Clojure's internal hashing algorithm, which has been known to change across
    ;; versions.  It would be better to use eight bytes from a SHA256 in production.
    (jdbc/execute! tx ["SELECT pg_advisory_xact_lock(?);" (hash url)])
    (if-let [{id :participant_addresses/participant_id} (jdbc/execute-one! tx ["SELECT participant_id FROM participant_addresses WHERE url = ?;" url])]
      id
      (let [id (random-uuid)]
        (jdbc/execute! tx ["INSERT INTO participants (id) VALUES (?);" id])
        (jdbc/execute! tx ["INSERT INTO participant_addresses (url, participant_id) VALUES (?, ?);" url id])
        id))))

(defn insert-message
  [ds {:keys [::message/from ::message/type body timestamp attachments]}]
  (let [id        (random-uuid)
        timestamp (java.util.Date/from (java.time.Instant/parse timestamp))]
    (jdbc/with-transaction [tx ds]
      (upsert-participant tx from)
      (jdbc/execute! tx ["INSERT INTO messages (id, \"from\", type, body, timestamp) VALUES (?, ?, ?, ?, ?);"
                         id from (name type) body timestamp])
      (doseq [url attachments]
        (jdbc/execute! tx ["INSERT INTO message_attachments (message_id, url) VALUES (?, ?);" id url]))
      {:id id
       :from from
       :type type
       :body body
       :timestamp timestamp})))
