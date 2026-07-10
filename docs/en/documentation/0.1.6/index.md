![Tentacolous](../../../assets/logonegro.png){ .content-logo }

Tentacolous Documentation

# Technical guide

Everything you need to install, configure and operate Tentacolous in a Spring Boot project.

This page expands the README with a more navigable structure. It keeps the real repository scope:
Tentacolous uses PostgreSQL infrastructure, an event table and a Spring poller to execute annotated Java methods.

[Overview](#overview)
[Requirements](#requirements)
[Version 0.1.6 dependency](#install)
[Configuration](#config)
[Entity mapping](#entity)
[Annotations](#annotations)
[Filter types](#filters)
[Operations](#operations)
[Invalid combinations](#invalid)
[Manual testing](#manual-testing)
[How it works](#internals)
[Security](#security)
[Production](#production)
[Tests](#tests)
[Limitations](#limitations)

## Overview {#overview}

Tentacolous is a Spring Boot library that runs Java methods when a database table receives an `INSERT`, `UPDATE`, or `DELETE`.

The important difference is that Tentacolous reacts to database changes regardless of where they originate.

**1.** It creates an event table.

**2.** It creates a PostgreSQL function.

**3.** It creates triggers for tables that have listeners.

**4.** The triggers write events into `db_change_event`.

**5.** A Spring poller reads those events.

**6.** Tentacolous converts the JSON payload into your Java entity.

**7.** Tentacolous runs the annotated method.

## Requirements {#requirements}

- Java 17 or higher.
- Spring Boot.
- A Spring Boot application with a configured `DataSource`.
- PostgreSQL for automatic trigger creation.

Automatic database infrastructure creation is currently implemented for PostgreSQL.

## Version 0.1.6 dependency {#install}

Use these coordinates when your application requires Tentacolous 0.1.6.

```groovy
implementation 'io.github.aimtone:tentacolous:0.1.6'
```

```xml
<dependency>
  <groupId>io.github.aimtone</groupId>
  <artifactId>tentacolous</artifactId>
  <version>0.1.6</version>
</dependency>
```

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

### Schema management modes

| Mode | Common use | Behavior |
| --- | --- | --- |
| `auto` | Development | Creates the table, function and triggers if needed. |
| `create` | Controlled development environments | Forces supported infrastructure creation. |
| `validate` | Production | Validates that infrastructure exists without creating it. |
| `none` | Production with migrations | Does not create or validate infrastructure. |

To get started, use `schema-management: auto`. In production, `validate` or `none` is usually a better choice because creating triggers requires elevated database permissions.

## Example entity {#entity}

Tentacolous uses the entity to know which database table it should listen to.

```java
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "persona")
public class Persona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String lastname;
    private String email;

    public Persona() {
    }

    // getters and setters
}
```

If the entity has `@Table(name = "persona")`, Tentacolous listens to the `persona` table. If it does not have `@Table`, Tentacolous infers the table name from the class name.

| Entity | Inferred table |
| --- | --- |
| `Persona` | `persona` |
| `UserAccount` | `user_account` |
| `PaymentTransaction` | `payment_transaction` |

That is why the annotations do not have a `table` parameter: the selected entity already represents the table.

## Annotation parameters {#annotations}

The three annotations have the same shape: `@UponInserting(...)`, `@UponUpdating(...)` and `@UponDeleting(...)`.

| Parameter | Required | Description |
| --- | --- | --- |
| `entity` | Yes | Entity class that represents the table and receives the deserialized payload. |
| `entityName` | No | Logical event name. If omitted, Tentacolous uses the class simple name. |
| `field` | Only with filters | Payload field to compare. |
| `valueType` | Only with filters | Type used to interpret `value`. |
| `value` | Only with filters | Expected value, always written as text. |
| `exclude` | No | Columns that should not be stored in the event payload. |

### About entityName

Most of the time, you do not need `entityName`. By default, `@UponInserting(entity = Persona.class)` uses `Persona`. The value is stored in `db_change_event.entity_name` and is used internally to match events with listeners.

Use `entityName` only for advanced cases where you need a stable logical name, for example when external infrastructure already writes events with a specific name.

### About exclude

`exclude` does not filter listeners. It prevents specific columns from being stored in the event JSON payload.

```java
@UponInserting(
    entity = User.class,
    exclude = {"password", "token", "secret_key"}
)
public void onUserInserted(User user) {
}
```

This matters because the event table may contain business data. You usually do not want secrets stored there.

## Filter types {#filters}

Filters always use this structure:

```text
field = "fieldName",
valueType = ValueType.TYPE,
value = "expected value"
```

If you use `valueType`, you must also define `field` and `value`.

| ValueType | value format | Example |
| --- | --- | --- |
| `STRING` | Exact text | `"APPROVED"` |
| `BOOLEAN` | `true` or `false` | `"true"` |
| `NUMBER` | Long integer number | `"1"` |
| `INTEGER` | Integer number | `"7"` |
| `LONG` | Long integer number | `"999"` |
| `DECIMAL` | Exact decimal | `"10.50"` |
| `DOUBLE` | Floating point decimal | `"3.14"` |
| `DATE` | ISO date | `"2026-07-07"` |
| `TIME` | ISO time | `"13:45:00"` |
| `DATETIME` | ISO instant or datetime | `"2026-07-07T13:45:00Z"` |
| `UUID` | Canonical UUID | `"550e8400-e29b-41d4-a716-446655440000"` |

## Operations {#operations}

### UponInserting

`@UponInserting` runs a method when an `INSERT` occurs on the entity table.

```java
@UponInserting(entity = Persona.class)
public void onPersonaInserted(Persona persona) {
    System.out.println("Inserted: " + persona.getEmail());
}
```

Use cases: run logic after a record is created, send a notification, publish a Kafka message or create an audit record.

#### Insert with explicit logical name

```java
@UponInserting(entity = Persona.class, entityName = "Persona")
public void onPersonaInserted(Persona persona) {
}
```

#### Insert with STRING filter

```java
@UponInserting(
    entity = Persona.class,
    field = "email",
    valueType = ValueType.STRING,
    value = "admin@example.com"
)
public void onAdminInserted(Persona persona) {
}
```

#### Insert with BOOLEAN filter

```java
@UponInserting(
    entity = User.class,
    field = "active",
    valueType = ValueType.BOOLEAN,
    value = "true"
)
public void onActiveUserInserted(User user) {
}
```

#### Insert with numeric filter

```java
@UponInserting(
    entity = User.class,
    field = "level",
    valueType = ValueType.LONG,
    value = "1"
)
public void onLevelOneUserInserted(User user) {
}
```

#### Insert with date filter

```java
@UponInserting(
    entity = Payment.class,
    field = "paymentDate",
    valueType = ValueType.DATE,
    value = "2026-07-07"
)
public void onPaymentDate(Payment payment) {
}
```

#### Insert with excluded columns

```java
@UponInserting(
    entity = User.class,
    exclude = {"password", "token"}
)
public void onUserInserted(User user) {
}
```

### UponUpdating

`@UponUpdating` runs a method when an `UPDATE` occurs on the entity table.

```java
@UponUpdating(entity = Persona.class)
public void onPersonaUpdated(Persona persona) {
    System.out.println("Updated: " + persona.getEmail());
}
```

Use cases: recalculate derived data, invalidate cache, notify other systems or synchronize with an external service.

#### Update with STRING filter

```java
@UponUpdating(
    entity = Payment.class,
    field = "status",
    valueType = ValueType.STRING,
    value = "APPROVED"
)
public void onPaymentApproved(Payment payment) {
}
```

#### Update with BOOLEAN filter

```java
@UponUpdating(
    entity = User.class,
    field = "enabled",
    valueType = ValueType.BOOLEAN,
    value = "false"
)
public void onUserDisabled(User user) {
}
```

#### Update with DATETIME filter

```java
@UponUpdating(
    entity = Invoice.class,
    field = "paidAt",
    valueType = ValueType.DATETIME,
    value = "2026-07-07T13:45:00Z"
)
public void onInvoicePaidAt(Invoice invoice) {
}
```

#### Update with excluded columns

```java
@UponUpdating(
    entity = User.class,
    exclude = {"password", "refresh_token"}
)
public void onUserUpdated(User user) {
}
```

### UponDeleting

`@UponDeleting` runs a method when a `DELETE` occurs on the entity table. For a delete, the payload contains the previous values of the record because the row no longer exists after deletion.

```java
@UponDeleting(entity = Persona.class)
public void onPersonaDeleted(Persona persona) {
    System.out.println("Deleted: " + persona.getEmail());
}
```

Use cases: clean up external resources, delete related data in another database, notify about deletions or register deletion audit data.

#### Delete with STRING filter

```java
@UponDeleting(
    entity = Persona.class,
    field = "email",
    valueType = ValueType.STRING,
    value = "admin@example.com"
)
public void onAdminDeleted(Persona persona) {
}
```

#### Delete with UUID filter

```java
@UponDeleting(
    entity = Session.class,
    field = "externalId",
    valueType = ValueType.UUID,
    value = "550e8400-e29b-41d4-a716-446655440000"
)
public void onSessionDeleted(Session session) {
}
```

#### Delete with excluded columns

```java
@UponDeleting(
    entity = User.class,
    exclude = {"password", "token"}
)
public void onUserDeleted(User user) {
}
```

## Invalid combinations {#invalid}

These combinations should fail when the application starts.

### Missing field

```java
@UponInserting(
    entity = Persona.class,
    valueType = ValueType.BOOLEAN,
    value = "true"
)
public void invalid(Persona persona) {
}
```

### Missing value

```java
@UponInserting(
    entity = Persona.class,
    field = "active",
    valueType = ValueType.BOOLEAN
)
public void invalid(Persona persona) {
}
```

### Method with more than one parameter

```java
@UponInserting(entity = Persona.class)
public void invalid(Persona persona, String other) {
}
```

### Incompatible parameter

```java
@UponInserting(entity = Persona.class)
public void invalid(String persona) {
}
```

## Manual testing {#manual-testing}

Assume this entity and listener:

```java
@Entity
@Table(name = "persona")
public class Persona {
    // ...
}

@UponInserting(entity = Persona.class)
public void inserted(Persona persona) {
    System.out.println("Persona inserted: " + persona.getEmail());
}
```

Start your Spring Boot application. Tentacolous should print logs similar to:

```text
Tentacolous registered 1 listener method(s)
Initializing Tentacolous schema using event table 'db_change_event'
Creating Tentacolous INSERT trigger for table 'persona' and entity 'Persona'
Starting Tentacolous poller
```

Then run this SQL in PostgreSQL:

```sql
INSERT INTO public.persona (email, lastname, "name")
VALUES ('test@example.com', 'Perez', 'Ana');
```

You should see this in your application console:

```java
Persona inserted: test@example.com
```

Inspect events and triggers with:

```sql
select *
from db_change_event
order by id desc;

select trigger_name, event_object_schema, event_object_table
from information_schema.triggers
where event_object_table = 'persona';
```

## How it works internally {#internals}

Tentacolous does not use JPA events. This is intentional: JPA events only work when the change goes through the same application. Tentacolous is designed to detect real database changes, even when they come from outside your application.

In PostgreSQL, Tentacolous creates:

- A `db_change_event` table.
- A `db_change_event_notify_change()` function.
- One trigger per table and operation.

The trigger stores `entity_name`, `operation`, `payload`, `status`, `attempts`, `last_error` and processing timestamps.

The poller looks for `PENDING` events, marks them as `PROCESSING`, runs the listener and finally marks them as `PROCESSED`. If an error happens, Tentacolous stores `last_error` and retries until `tentacolous.max-attempts` is reached.

## Security {#security}

- Do not store secrets in the payload.
- Use `exclude` for sensitive columns.
- Protect `db_change_event` with proper database permissions.
- In production, use `schema-management=validate` or `schema-management=none`.
- Keep listeners idempotent because retries may happen.

## Production {#production}

- Create the table, function and triggers using controlled migrations.
- Use `tentacolous.schema-management=validate` to verify infrastructure.
- Monitor `FAILED` events.
- Clean up or archive `db_change_event`.
- Avoid slow logic inside listeners.
- Publish to a queue if the process is heavy.

## Running Tentacolous tests {#tests}

From the library folder:

```shell
mvn test
```

To clean, compile, test and install:

```shell
mvn clean install
```

Expected result:

```text
BUILD SUCCESS
```

## Current limitations {#limitations}

- Automatic infrastructure creation is currently implemented only for PostgreSQL.
- Tentacolous does not use native CDC such as Debezium or logical replication.
- Polling is simple. For very high-volume systems, native CDC or a specialized queue may be a better fit.
