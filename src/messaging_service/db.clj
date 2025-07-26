(ns messaging-service.db
 (:require
  [next.jdbc :as jdbc]))

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
      message_id UUID DEFAULT gen_random_uuid() NOT NULL,
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

(defn insert-message [ds message]
  nil)
