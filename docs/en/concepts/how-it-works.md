## How it works internally {#internals}

Tentacolous does not use JPA events. This is intentional: JPA events only work when the change goes through the same application. Tentacolous is designed to detect real database changes, even when they come from outside your application.

For the detected database, Tentacolous creates:

- A `db_change_event` table.
- A `db_change_event_notify_change()` function.
- One trigger per table and operation.

The trigger stores `entity_name`, `operation`, `payload`, `old_payload`, `record_key`, `status`, `attempts`, `last_error` and processing timestamps.

`payload` contains the current row for inserts and updates, and the deleted row for deletes. `old_payload` contains the previous row only for updates. `record_key` identifies the affected record, using the entity `@Id` column when available or `id` otherwise.

When a listener asks for history, Tentacolous queries previous `INSERT` and `UPDATE` payloads for the same `entity_name` and `record_key`, ordered from oldest to newest.

The poller looks for `PENDING` events, marks them as `PROCESSING`, runs the listener and finally marks them as `PROCESSED`. If an error happens, Tentacolous stores `last_error` and retries until `tentacolous.max-attempts` is reached.
