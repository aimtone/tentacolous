# Message Brokers (Kafka and RabbitMQ)

Tentacolous can forward every detected database change to a message broker, in addition to (or
instead of) calling in-process listener methods. This turns Tentacolous into a lightweight
change-data-capture relay: no Kafka Connect, no access to the WAL/binlog, just triggers and the
event table you already have.

## How it works

```
change in the DB  ->  trigger writes db_change_event  ->  poller reads it
                                                              |
                                        +---------------------+---------------------+
                                        |                                           |
                              @Upon... listener methods                    ChangeEventSink(s)
                                                                          (Kafka, RabbitMQ, ...)
```

Every `ChangeEventSink` bean is invoked once per event, after the listener methods. If a sink
throws, the event is **not** marked as processed and follows the normal retry flow
(`tentacolous.max-attempts`). Delivery is therefore **at-least-once**: a consumer must be able to
receive the same event twice. Use the `eventId` (unique, increasing) as the deduplication key.

## Choosing which tables to forward

A trigger only exists for a table that has at least one listener **or** a capture declaration. If
you only want to stream a table and have no `@Upon...` method for it, declare a capture. There are
two equivalent ways; use whichever fits your codebase.

### Way 1 — the `@TentacolousCapture` annotation

Put it on any Spring-managed class (its body can stay empty):

```java
@Configuration
@TentacolousCapture(entity = Person.class)
@TentacolousCapture(
    entity = Order.class,
    actions = {ActionListener.INSERT, ActionListener.UPDATE},
    field = "status", valueType = ValueType.STRING, value = "APPROVED",
    exclude = {"internal_notes"})
public class TentacolousCaptureConfig {
}
```

### Way 2 — a `Capture` bean

A `@Bean` method per capture — the class is never empty, so tools like SonarQube do not flag it,
and the entity reference is compile-checked:

```java
@Configuration
public class TentacolousConfig {

    @Bean
    Capture personCapture() {
        return Capture.of(Person.class);
    }

    @Bean
    Capture approvedOrderCapture() {
        return Capture.of(Order.class)
                .operations(DbOperation.INSERT, DbOperation.UPDATE)
                .where("status", ValueType.STRING, "APPROVED")
                .exclude("internal_notes");
    }

    // a programmatic filter, injected as a bean
    @Bean
    Capture activePersonCapture(ActivePersonFilter filter) {
        return Capture.of(Person.class).filter(filter);
    }
}
```

You do not instantiate these yourself — Spring runs the `@Bean` method at startup and Tentacolous
collects every `Capture` bean automatically.

### Which one?

| | `@TentacolousCapture` | `Capture` bean |
|---|---|---|
| Class can be empty | Yes (some linters dislike it) | No — has `@Bean` methods |
| Entity is compile-checked | Yes | Yes |
| Conditional per profile / property | No | Yes (`@Bean @Profile(...)`, `@ConditionalOnProperty`) |
| Programmatic filter | `filter = MyFilter.class` (resolved as a bean) | `.filter(myFilterBean)` (injected) |
| Testable in isolation | Needs a context | Yes, it is a plain object |

Both feed the same registry; you can mix them. `entity` is resolved to a table name and key exactly
like a listener (JPA `@Table` / `@Id`, or the snake-cased class name).

### Capture options

| Annotation | `Capture` bean | Meaning |
|---|---|---|
| `actions` | `.operations(...)` / `.operation(...)` | Operations to capture. Default: all three. |
| `entityName` | `.entityName(...)` | Logical event name. Default: class simple name. |
| `exclude` | `.exclude(...)` | Columns removed from the stored and forwarded payload. |
| `field` + `valueType` + `value` | `.where(field, valueType, value)` | Declarative filter: forward only changes where the field equals the value. |
| `filter` | `.filter(...)` | Programmatic `TentacolousFilter<T>`: forward only changes it accepts. Wins over the declarative filter. |
| `order` | `.order(...)` | Ordering hint. |

### How capture filters affect the sinks

Filters on a capture decide **which changes reach the sinks** — they do not run any Java method.

| Situation | What the sinks receive |
|---|---|
| Entity has an `@Upon...` listener, no capture | Every change (unchanged default) |
| Entity has a capture with a filter | Only changes that pass the filter |
| Entity has a listener **and** a filtered capture | The listener still runs with its own filter; the sinks get only what the capture accepts |
| Several captures for the same entity + operation | Forwarded if **any** of them accepts (OR) |

A custom filter receives a `TentacolousFilterContext` with the current entity, the previous entity
(updates), `getChangedFields()` / `hasChanged(...)`, `getRecordKey()` and `getEventId()` — the same
context as a listener filter.

## Message format

Both sinks share a `format` option:

| `format`   | Body |
|------------|------|
| `envelope` (default) | Self-describing JSON with metadata and `before` / `after` |
| `raw` | The row payload exactly as the trigger produced it |

Envelope example (`UPDATE`):

```json
{
  "eventId": 42,
  "entity": "Person",
  "operation": "UPDATE",
  "recordKey": "7",
  "observedAt": "2026-09-01T12:00:00Z",
  "before": { "id": 7, "name": "Ana" },
  "after":  { "id": 7, "name": "Ana Maria" }
}
```

For `INSERT`, `before` is `null`. For `DELETE`, `after` is `null` and `before` holds the removed row.

Regardless of the format, these message headers are always available (unless `add-headers: false`):

- `tentacolous-event-id`
- `tentacolous-entity`
- `tentacolous-operation`

## Kafka

Add Spring for Apache Kafka to your application (Tentacolous declares it as an optional dependency):

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

Configure it:

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092

tentacolous:
  kafka:
    enabled: true
    topic-prefix: "cdc."      # topic = cdc.<entityName>, e.g. cdc.Person
    # topic: all-changes      # or force a single fixed topic
    format: envelope
    add-headers: true
    send-timeout: 10s
```

- The record **key** is the `recordKey`, so all changes to the same row land on the same partition
  and stay ordered.
- The producer runs with `acks=all` and `enable.idempotence=true` by default; override through the
  standard `spring.kafka.producer.*` properties.
- `send-timeout` is how long the poller waits for the broker acknowledgement before failing the
  event (it will be retried on the next poll).

Consumer side, nothing special is required:

```java
@KafkaListener(topics = "cdc.Person", groupId = "billing")
public void onPersonChange(ConsumerRecord<String, byte[]> record) {
    String json = new String(record.value(), StandardCharsets.UTF_8);
    // parse the envelope, deduplicate on the tentacolous-event-id header
}
```

## RabbitMQ

Add Spring AMQP:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

Configure it:

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

tentacolous:
  rabbitmq:
    enabled: true
    exchange: tentacolous          # durable topic exchange, declared on startup
    routing-key-prefix: ""         # routing key = <prefix><entity>.<operation>, lower-cased
    format: envelope
    declare-exchange: true
    add-headers: true
```

Messages are published to a **topic exchange** with routing key `entity.operation`, for example
`person.update` or `order.delete`. Bind your queues with the pattern you need:

```java
@Bean
Queue billingQueue() {
    return QueueBuilder.durable("billing.person").build();
}

@Bean
Binding billingBinding(Queue billingQueue, TopicExchange tentacolousRabbitExchange) {
    return BindingBuilder.bind(billingQueue).to(tentacolousRabbitExchange).with("person.*");
}

@RabbitListener(queues = "billing.person")
public void onPersonChange(Message message) {
    // message.getBody() is the JSON envelope
}
```

Messages are persistent and carry the `tentacolous-event-id` as the AMQP `messageId`.

## Using both at once

Enabling `tentacolous.kafka.enabled` and `tentacolous.rabbitmq.enabled` together registers both
sinks. Each event is published to both; if either fails the whole event is retried, so both
transports may see a redelivery. Keep consumers idempotent.

## Writing your own sink

Implement `ChangeEventSink` and expose it as a bean:

```java
@Component
public class WebhookSink implements ChangeEventSink {

    @Override
    public String name() {
        return "webhook";
    }

    @Override
    public boolean supports(ChangeEvent event) {
        return "Person".equals(event.getEntityName());
    }

    @Override
    public void publish(ChangeEvent event) throws Exception {
        // POST event.getPayload() somewhere; block until acknowledged
    }
}
```

Return a lower `order()` to run before the broker sinks.
