# Changelog

All notable changes to Tentacolous will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/).

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
