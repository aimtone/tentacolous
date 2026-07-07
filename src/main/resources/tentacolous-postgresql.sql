CREATE TABLE IF NOT EXISTS db_change_event (
    id BIGSERIAL PRIMARY KEY,
    entity_name VARCHAR(255) NOT NULL,
    operation VARCHAR(20) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    processed BOOLEAN NOT NULL DEFAULT false,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processing_started_at TIMESTAMP NULL,
    processed_at TIMESTAMP NULL
);

CREATE OR REPLACE FUNCTION db_change_event_notify_change()
RETURNS trigger AS $$
DECLARE
    payload_json jsonb;
    excluded_columns text[] := TG_ARGV[1]::text[];
BEGIN
    payload_json := CASE
        WHEN TG_OP = 'DELETE' THEN to_jsonb(OLD)
        ELSE to_jsonb(NEW)
    END;

    IF excluded_columns IS NOT NULL THEN
        payload_json := payload_json - excluded_columns;
    END IF;

    INSERT INTO db_change_event(entity_name, operation, payload)
    VALUES (TG_ARGV[0], TG_OP, payload_json::text);

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
-- EXECUTE FUNCTION db_change_event_notify_change('User', '{}');
--
-- CREATE TRIGGER users_tentacolous_listener_update
-- AFTER UPDATE ON users
-- FOR EACH ROW
-- EXECUTE FUNCTION db_change_event_notify_change('User', '{password,token}');
--
-- CREATE TRIGGER users_tentacolous_listener_delete
-- AFTER DELETE ON users
-- FOR EACH ROW
-- EXECUTE FUNCTION db_change_event_notify_change('User', '{}');
