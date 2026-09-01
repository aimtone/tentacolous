## Migrating to 0.3.0 {#migration-030}

Version `0.3.0` adds message-broker sinks and is fully backward compatible. Nothing changes for an application that does not use them.

1. Update the dependency to `0.3.0`.
2. Only if you want to forward changes to a broker: add `spring-kafka` or `spring-boot-starter-amqp` (declared as optional by Tentacolous) and set `tentacolous.kafka.enabled` / `tentacolous.rabbitmq.enabled`.
3. Keep broker consumers idempotent: delivery is at-least-once, keyed by `eventId`.

See [Message brokers](../concepts/message-brokers.md) for the full guide.

## Migrating to 0.2.0 {#migration}

Version `0.2.0` keeps the listener API from `0.1.8` and adds database dialects. Existing PostgreSQL applications do not need to change their annotations or Tentacolous properties.

1. Update the dependency to `0.2.0`.
2. Keep only the JDBC driver for the database used by the application.
3. Ensure the database user can create tables and triggers when `schema-management` is `auto` or `create`.
4. Run the application once in a non-production environment and validate INSERT, UPDATE, and DELETE listeners.

Applications using `schema-management=none` remain responsible for installing database-specific event infrastructure manually.

For SQLite, use a record key that is not reused during event-history retention. Prefer UUID or `INTEGER PRIMARY KEY AUTOINCREMENT` for business tables.
