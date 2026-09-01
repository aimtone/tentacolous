## Application configuration {#config}

Tentacolous needs your application to have a database connection. Example `application.yml`:

```yaml
tentacolous:
  enabled: true
  schema-management: auto
  event-table: db_change_event
  poll-interval: 1s
  initial-delay: 0s
  batch-size: 100
  max-attempts: 3

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
```

### Available properties

| Property | Default | What it does |
| --- | --- | --- |
| `tentacolous.enabled` | `true` | Enables or disables Tentacolous. |
| `tentacolous.schema-management` | `auto` | Defines whether Tentacolous creates, validates, or ignores database infrastructure. |
| `tentacolous.event-table` | `db_change_event` | Name of the event table. |
| `tentacolous.poll-interval` | `1s` | How often pending events are read. |
| `tentacolous.initial-delay` | `0s` | Delay before the poller starts. |
| `tentacolous.batch-size` | `100` | Maximum number of events read in each cycle. |
| `tentacolous.max-attempts` | `3` | Retry limit before an event is marked as `FAILED`. |

### Message-broker sinks

These properties are only read when the matching Spring dependency is on the classpath. Full guide: [Message brokers](message-brokers.md).

| Property | Default | What it does |
| --- | --- | --- |
| `tentacolous.kafka.enabled` | `false` | Publishes every change to Kafka. |
| `tentacolous.kafka.topic` | *(empty)* | Fixed topic. When empty, the topic is `topic-prefix` + entity name. |
| `tentacolous.kafka.topic-prefix` | *(empty)* | Prefix for the per-entity topic, e.g. `cdc.`. |
| `tentacolous.kafka.format` | `envelope` | `envelope` (before/after + metadata) or `raw` (row payload). |
| `tentacolous.kafka.add-headers` | `true` | Adds `tentacolous-*` headers. |
| `tentacolous.kafka.send-timeout` | `10s` | Wait for the broker ack before failing the event. |
| `tentacolous.rabbitmq.enabled` | `false` | Publishes every change to RabbitMQ. |
| `tentacolous.rabbitmq.exchange` | `tentacolous` | Topic exchange to publish to. |
| `tentacolous.rabbitmq.routing-key-prefix` | *(empty)* | Prefix for the `entity.operation` routing key. |
| `tentacolous.rabbitmq.format` | `envelope` | `envelope` or `raw`. |
| `tentacolous.rabbitmq.declare-exchange` | `true` | Declares the durable topic exchange on startup. |
| `tentacolous.rabbitmq.add-headers` | `true` | Adds `tentacolous-*` headers. |

### Schema management modes

| Mode | Common use | Behavior |
| --- | --- | --- |
| `auto` | Development | Creates the table, function and triggers if needed. |
| `create` | Controlled development environments | Forces supported infrastructure creation. |
| `validate` | Production | Validates that infrastructure exists without creating it. |
| `none` | Production with migrations | Does not create or validate infrastructure. |

To get started, use `schema-management: auto`. In production, `validate` or `none` is usually a better choice because creating triggers requires elevated database permissions.
