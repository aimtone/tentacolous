# Changelog

All notable changes to Tentacolous will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/).

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
