# Tentacolous 0.2.0 {#top}

Version `0.2.0` makes Tentacolous database-agnostic while keeping one library artifact and the existing listener API.

## Supported databases

| Database | Automatic table and triggers | Previous payload | History |
|---|---:|---:|---:|
| PostgreSQL 16 | Yes | Yes | Yes |
| MySQL 8.4 | Yes | Yes | Yes |
| MariaDB 11.8 | Yes | Yes | Yes |
| SQL Server 16 | Yes | Yes | Yes |
| Oracle Database 23 | Yes | Yes | Yes |
| SQLite 3.53 with JSON1 | Yes | Yes | Yes |

The application supplies the matching JDBC driver. Tentacolous detects the product through JDBC metadata and selects its dialect automatically.

## Installation

```xml
<dependency>
  <groupId>io.github.aimtone</groupId>
  <artifactId>tentacolous</artifactId>
  <version>0.2.0</version>
</dependency>
```

## Compatibility contract

All six dialects passed the same 28-listener end-to-end scenario, including operation-specific and generic listeners, declarative and custom filters, ordering, excluded columns, previous entities, and history as `List<Entity>` and `Entity[]`.

## SQLite consideration

SQLite may reuse a deleted `INTEGER PRIMARY KEY`. Because history is correlated by entity name and record key, use `INTEGER PRIMARY KEY AUTOINCREMENT`, UUID, or another key that is not reused during history retention.

See the [current technical guide](../index.md) for the complete API and configuration reference.
