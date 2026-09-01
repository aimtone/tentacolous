# Tentacolous 0.3.0 {#top}

Version `0.3.0` adds message-broker sinks. Every database change detected by Tentacolous can now be
forwarded to Kafka or RabbitMQ, in addition to (or instead of) calling in-process listener methods.
The listener and filter API from `0.2.0` is unchanged.

## Installation

```xml
<dependency>
  <groupId>io.github.aimtone</groupId>
  <artifactId>tentacolous</artifactId>
  <version>0.3.0</version>
</dependency>
```

Add `spring-kafka` or `spring-boot-starter-amqp` to your application only when you enable the
matching sink. Tentacolous declares both as optional dependencies.

## What is new

- **`ChangeEventSink` SPI.** Any Spring bean implementing it receives every detected change after
  the listener methods run. Delivery is at-least-once; a sink failure keeps the event `PENDING` and
  reuses the existing retry flow. Deduplicate on `eventId`.
- **Kafka sink** (`tentacolous.kafka.*`). One record per event, keyed by the record key for
  per-row ordering, with `acks=all` and producer idempotence by default.
- **RabbitMQ sink** (`tentacolous.rabbitmq.*`). Publishes to a durable topic exchange with routing
  key `entity.operation`.
- **Message formats.** `envelope` (default, with `before` / `after` and metadata) or `raw`. The
  headers `tentacolous-event-id`, `tentacolous-entity` and `tentacolous-operation` are always set.
- **Captures.** Forward a table to the configured sinks without writing a listener method, via the
  `@TentacolousCapture` annotation or an equivalent `Capture` bean. Both accept the same options as
  the `@Upon...` annotations — operations, `entityName`, `exclude`, a declarative filter, a
  programmatic `TentacolousFilter`, and `order` — and their filters decide which changes reach the
  sinks.

## Minimal configuration

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
  rabbitmq:
    host: localhost
    username: guest
    password: guest

tentacolous:
  kafka:
    enabled: true
    topic-prefix: "cdc."
  rabbitmq:
    enabled: true
    exchange: tentacolous
```

## Compatibility

- Existing `0.2.x` applications are unaffected: the sinks are opt-in and the broker dependencies
  are optional.
- All six database dialects (PostgreSQL, MySQL, MariaDB, SQL Server, Oracle, SQLite) are supported
  as in `0.2.0`.

See the [current technical guide](../index.md) for the complete API and configuration reference,
including the [Message brokers](../index.md#message-brokers) section.
