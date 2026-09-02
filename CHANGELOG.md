# Changelog

All notable changes to Tentacolous will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/).

## [0.3.0] - 2026-09-01

### Added
- Added the `ChangeEventSink` SPI: any Spring bean implementing it receives every detected change
  after the in-process listener methods run. Delivery is at-least-once; failures reuse the existing
  retry flow.
- Added a native Kafka sink (`tentacolous.kafka.*`), enabled when `spring-kafka` is on the
  classpath. Publishes one record per event, keyed by the record key, with `acks=all` and producer
  idempotence by default.
- Added a native RabbitMQ sink (`tentacolous.rabbitmq.*`), enabled when `spring-boot-starter-amqp`
  is on the classpath. Publishes to a durable topic exchange with routing key `entity.operation`.
- Added the `envelope` (default) and `raw` message formats, plus `tentacolous-event-id`,
  `tentacolous-entity` and `tentacolous-operation` message headers.
- Added captures to forward a table to the configured sinks without writing a Java listener method,
  either with the `@TentacolousCapture` annotation or an equivalent `Capture` bean. Both accept the
  same options as the `@Upon...` annotations (operations, `entityName`, `exclude`, declarative
  filter, programmatic `TentacolousFilter`, `order`); the capture filters decide which changes reach
  the sinks.

### Changed
- `spring-kafka` and `spring-boot-starter-amqp` are declared as optional dependencies; existing
  applications that do not use them are unaffected.

### Documentation
- Added an Architecture FAQ (English and Spanish) covering the Debezium comparison, delivery and
  ordering guarantees, listener and crash failure modes, the Kafka/broker path, no-code captures,
  scaling limits, database load, security, and the hard limitations.

## [0.2.0] - 2026-07-11

### Added
- Added automatic database dialect detection through JDBC metadata.
- Added complete trigger, event-table, payload, previous-payload, history, pagination, and boolean handling for PostgreSQL, MySQL, MariaDB, SQL Server, Oracle, and SQLite.
- Added database-specific JSON generation and column metadata discovery.
- Added a shared dialect resolver used by schema management, polling, and event history.

### Changed
- Moved PostgreSQL-specific polling and history SQL behind the `DatabaseDialect` contract.
- Tentacolous remains a single JAR; applications only need to provide the JDBC driver for their database.
- Updated documentation and examples for database-agnostic configuration.

### Fixed
- Normalized Oracle JSON object keys to lowercase so payloads deserialize into standard Java property names.
- Removed PostgreSQL-only `LIMIT` and `payload::jsonb` expressions from common execution paths.

### Validation
- Validated the full 28-listener end-to-end matrix on PostgreSQL 16, MySQL 8.4, MariaDB 11.8, SQL Server 16, Oracle Database 23, and SQLite 3.53.
- The matrix covers operation-specific and generic listeners, declarative and custom filters, ordering, excluded columns, current/previous entities, and list/array history.

### Known considerations
- SQLite can reuse a deleted `INTEGER PRIMARY KEY` value unless the business table uses `AUTOINCREMENT`. Reused record keys can join otherwise unrelated event histories; use non-reusable keys during the history retention period.

## [0.1.8] - 2026-07-10

### Added
- Added Spring-managed programmatic filters for `@UponInserting`, `@UponUpdating`, `@UponDeleting`, and `@TentacolousListener` through the new `filter` annotation parameter.
- Added `TentacolousFilter<T>` and `TentacolousFilterContext<T>` with access to the current entity, the previous entity for updates, and the database operation.
- Added the generic `@TentacolousListener` annotation with `ActionListener.INSERT`, `ActionListener.UPDATE`, and `ActionListener.DELETE` as an alternative to the operation-specific annotations.
- Added versioned English and Spanish documentation for `0.1.8`, including examples for generic listeners and custom filters.

### Behavior
- A custom filter has priority over declarative `field`, `valueType`, and `value` filters when both styles are configured, and Tentacolous logs a warning.
- Declarative filters must now define `field`, `valueType`, and `value` together.
- Existing `@UponInserting`, `@UponUpdating`, and `@UponDeleting` listeners remain fully supported.
- Custom filter entity types are validated when listeners are registered.
- Methods can declare multiple Tentacolous listener annotations only when each annotation targets a different operation.
- Filter contexts now expose the event ID, entity name, and record key.
- Filtered listeners reuse deserialized entities when invoked.
- Filter and listener errors now include the operation, entity, method, and event details.
- All listener annotations now support ascending execution order through the `order` parameter.
- Filter contexts now expose `getChangedFields()` and `hasChanged(...)` for update events.

## [0.1.7] - 2026-07-09

### Added
- Added optional previous-entity support for `@UponUpdating` listeners.
- Added optional change history support for `@UponUpdating` and `@UponDeleting` listeners.
- Added support for history parameters as `List<Entity>` or `Entity[]`.
- Added `old_payload` and `record_key` event metadata to support previous values and record history.
- Added automatic schema migration for the new event columns when schema management is enabled.

### Changed
- `@UponUpdating` now supports one, two, or three parameters:
  - current entity
  - current entity and previous entity
  - current entity, previous entity, and history
- `@UponDeleting` now supports one or two parameters:
  - deleted entity
  - deleted entity and history
- Updated README and documentation examples for version `0.1.7`.

### Notes
- Projects using `tentacolous.schema-management=none` must update their manual database infrastructure to include the new `old_payload` and `record_key` columns and the updated trigger function.

## [0.1.6] - 2026-07-07

### Added
- Initial public release of Tentacolous.
- Core library functionality.
- Initial API.
- Basic documentation and usage examples.

### Fixed
- No fixes yet.

### Known Issues
- This is an early release.
- Some features may change in future versions.
- Bugs may still exist.
