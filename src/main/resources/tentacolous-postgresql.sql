CREATE TABLE IF NOT EXISTS db_change_event (
    id BIGSERIAL PRIMARY KEY,
    entity_name VARCHAR(255) NOT NULL,
    operation VARCHAR(20) NOT NULL,
    payload TEXT NOT NULL,
    old_payload TEXT NULL,
    record_key TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    processed BOOLEAN NOT NULL DEFAULT false,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processing_started_at TIMESTAMP NULL,
    processed_at TIMESTAMP NULL
);

ALTER TABLE db_change_event ADD COLUMN IF NOT EXISTS old_payload TEXT NULL;
ALTER TABLE db_change_event ADD COLUMN IF NOT EXISTS record_key TEXT NULL;

CREATE OR REPLACE FUNCTION db_change_event_notify_change()
RETURNS trigger AS $$
DECLARE
    payload_json jsonb;
    old_payload_json jsonb;
    excluded_columns text[] := TG_ARGV[1]::text[];
    record_key_field text := COALESCE(NULLIF(TG_ARGV[2], ''), 'id');
    record_key_value text;
BEGIN
    payload_json := CASE
        WHEN TG_OP = 'DELETE' THEN to_jsonb(OLD)
        ELSE to_jsonb(NEW)
    END;
    old_payload_json := CASE
        WHEN TG_OP = 'UPDATE' THEN to_jsonb(OLD)
        ELSE NULL
    END;
    record_key_value := payload_json ->> record_key_field;

    IF excluded_columns IS NOT NULL THEN
        payload_json := payload_json - excluded_columns;

        IF old_payload_json IS NOT NULL THEN
            old_payload_json := old_payload_json - excluded_columns;
        END IF;
    END IF;

    INSERT INTO db_change_event(entity_name, operation, payload, old_payload, record_key)
    VALUES (TG_ARGV[0], TG_OP, payload_json::text, old_payload_json::text, record_key_value);

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Example:
-- CREATE TRIGGER users_tentacolous_listener_insert
-- AFTER INSERT ON users
-- FOR EACH ROW
-- EXECUTE FUNCTION db_change_event_notify_change('User', '{}', 'id');
--
-- CREATE TRIGGER users_tentacolous_listener_update
-- AFTER UPDATE ON users
-- FOR EACH ROW
-- EXECUTE FUNCTION db_change_event_notify_change('User', '{password,token}', 'id');
--
-- CREATE TRIGGER users_tentacolous_listener_delete
-- AFTER DELETE ON users
-- FOR EACH ROW
-- EXECUTE FUNCTION db_change_event_notify_change('User', '{}', 'id');
