![Tentacolous](../../assets/logonegro.png){ .content-logo }

Tentacolous Documentation

# Technical guide

Everything you need to install, configure and operate Tentacolous in a Spring Boot project.

Learn how to add the dependency, configure event processing, and create Java listeners that react to real changes in PostgreSQL, MySQL, MariaDB, SQL Server, Oracle, or SQLite.

## Overview {#overview}

Tentacolous is a Spring Boot library that runs Java methods when a database table receives an `INSERT`, `UPDATE`, or `DELETE`.

The important difference is that Tentacolous reacts to database changes regardless of where they originate.

**Version 0.2.0 adds database-agnostic execution.** Tentacolous detects the JDBC product and selects database-specific table, trigger, JSON, pagination, and history behavior while preserving the existing listener and filter API.

**1.** It creates an event table.

**2.** It creates database-specific trigger infrastructure.

**3.** It creates triggers for tables that have listeners.

**4.** The triggers write events into `db_change_event`.

**5.** A Spring poller reads those events.

**6.** Tentacolous converts the JSON payload into your Java entity.

**7.** Tentacolous runs the annotated method.

## Requirements {#requirements}

- Java 17 or higher.
- Spring Boot.
- A Spring Boot application with a configured `DataSource`.
- PostgreSQL, MySQL, MariaDB, SQL Server, Oracle or SQLite, plus its JDBC driver.

Automatic database infrastructure is available for PostgreSQL, MySQL, MariaDB, SQL Server, Oracle, and SQLite.

## Dependency snippets {#dependency}

### Gradle

```groovy
implementation 'io.github.aimtone:tentacolous:0.2.0'
```

### Maven

```xml
<dependency>
  <groupId>io.github.aimtone</groupId>
  <artifactId>tentacolous</artifactId>
  <version>0.2.0</version>
</dependency>
```

## Migrating to 0.2.0 {#migration}

Version `0.2.0` remains compatible with existing listeners. PostgreSQL users do not need to replace annotations; other databases need their matching JDBC driver and trigger permissions when automatic schema management is enabled.

- Use `@TentacolousListener` only when you prefer the generic annotation with `action`.
- Custom filters are optional Spring beans extending `TentacolousFilter<T>`.
- The filter generic type must be compatible with the listener entity.
- If a custom filter is declared together with `field`, `valueType`, or `value`, the custom filter has priority and Tentacolous logs a warning.
- Declarative filters must define `field`, `valueType`, and `value` together.
- Each method may declare one listener annotation per operation.

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
@Entity
@Table(name = "person")
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String lastname;
    private String email;

    public Person() {
    }

    // getters and setters
}
```

If the entity has `@Table(name = "person")`, Tentacolous listens to the `person` table. If it does not have `@Table`, Tentacolous infers the table name from the class name.

| Entity | Inferred table |
| --- | --- |
| `Person` | `person` |
| `UserAccount` | `user_account` |
| `PaymentTransaction` | `payment_transaction` |

That is why the annotations do not have a `table` parameter: the selected entity already represents the table.

## Listeners and operations {#operations}

Start with the operation-specific annotations. They remain fully supported in 0.2.0 and now also accept custom filters. The generic listener is explained afterward as an optional alternative.

### UponInserting

`@UponInserting` runs a method when an `INSERT` occurs on the entity table.

```java
@UponInserting(entity = Person.class)
public void onPersonInserted(Person person) {
    System.out.println("Inserted: " + person.getEmail());
}
```

Use cases: run logic after a record is created, send a notification, start a workflow or create an audit record.

#### Insert with explicit logical name

```java
@UponInserting(entity = Person.class, entityName = "Person")
public void onPersonInserted(Person person) {
}
```

#### Insert with STRING filter

```java
@UponInserting(
    entity = Person.class,
    field = "email",
    valueType = ValueType.STRING,
    value = "admin@example.com"
)
public void onAdminInserted(Person person) {
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
@UponUpdating(entity = Person.class)
public void onPersonUpdated(Person person) {
    System.out.println("Updated: " + person.getEmail());
}
```

You can also receive the previous record values by adding a second parameter of the same entity type.

```java
@UponUpdating(entity = Person.class)
public void onPersonUpdated(Person newPerson, Person oldPerson) {
}
```

If you add a third parameter, Tentacolous provides the previous snapshots for that same record, ordered from oldest to newest.

```java
@UponUpdating(entity = Person.class)
public void onPersonUpdated(Person newPerson, Person oldPerson, List<Person> history) {
}
```

The history parameter can be a `List<Person>` or `Person[]`. Tentacolous matches the record by its `@Id` column when available, otherwise by `id`.

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

Excluded columns are removed from both the new and previous payloads.

### UponDeleting

`@UponDeleting` runs a method when a `DELETE` occurs on the entity table. For a delete, the payload contains the previous values of the record because the row no longer exists after deletion.

```java
@UponDeleting(entity = Person.class)
public void onPersonDeleted(Person person) {
    System.out.println("Deleted: " + person.getEmail());
}
```

You can also receive the previous snapshots for that same record by adding a second parameter.

```java
@UponDeleting(entity = Person.class)
public void onPersonDeleted(Person person, List<Person> history) {
}
```

The history parameter can be a `List<Person>` or `Person[]`.

Use cases: clean up external resources, delete related data in another database, notify about deletions or register deletion audit data.

#### Delete with STRING filter

```java
@UponDeleting(
    entity = Person.class,
    field = "email",
    valueType = ValueType.STRING,
    value = "admin@example.com"
)
public void onAdminDeleted(Person person) {
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

### Generic alternative: TentacolousListener

`@TentacolousListener` is the generic alternative. Its `action` determines the operation and valid method signature.

```java
@TentacolousListener(entity = Person.class, action = ActionListener.INSERT)
public void onPersonInserted(Person person) {
}
```

```java
@TentacolousListener(entity = Person.class, action = ActionListener.UPDATE)
public void onPersonUpdated(Person newPerson, Person oldPerson, List<Person> history) {
}
```

```java
@TentacolousListener(entity = Person.class, action = ActionListener.DELETE)
public void onPersonDeleted(Person person, List<Person> history) {
}
```

The parameter rules are identical to the corresponding operation-specific annotations.

## Annotation parameters {#annotations}

The operation-specific annotations and `@TentacolousListener(...)` share the same listener configuration.

| Parameter | Required | Description |
| --- | --- | --- |
| `entity` | Yes | Entity class that represents the table and receives the deserialized payload. |
| `action` | Only for `@TentacolousListener` | `ActionListener.INSERT`, `ActionListener.UPDATE`, or `ActionListener.DELETE`. |
| `entityName` | No | Logical event name. If omitted, Tentacolous uses the class simple name. |
| `field` | Only with filters | Payload field to compare. |
| `valueType` | Only with filters | Type used to interpret `value`. |
| `value` | Only with filters | Expected value, always written as text. |
| `filter` | No | Spring bean extending `TentacolousFilter<T>`. It replaces the declarative filter. |
| `order` | No | Listener execution order. Lower values run first; the default is `0`. |
| `exclude` | No | Columns that should not be stored in the event payload. |

### Listener ordering

Use `order` when several listeners handle the same entity and operation. Ordering works across specific and generic annotations.

```java
@UponUpdating(entity = Person.class, order = 10)
public void updateProfile(Person person) {
}

@TentacolousListener(
    entity = Person.class,
    action = ActionListener.UPDATE,
    order = 20
)
public void registerAudit(Person person) {
}
```

If a listener fails, subsequent listeners are not invoked and the event follows the normal retry flow. Listener side effects should be idempotent.

### About entityName

Most of the time, you do not need `entityName`. By default, `@UponInserting(entity = Person.class)` uses `Person`. The value is stored in `db_change_event.entity_name` and is used internally to match events with listeners.

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

`field`, `valueType`, and `value` must be declared together. If one is missing, Tentacolous fails during listener scanning and prints an example of the valid form.

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

## Custom filters {#custom-filters}

Programmatic filtering is the main addition in `0.2.0`. Define the rule once in a reusable class and reference it from any listener annotation. The filter class must be a Spring bean.

```java
@Component
public class ActivePersonFilter extends TentacolousFilter<Person> {

    @Override
    public boolean accept(TentacolousFilterContext<Person> context) {
        Person person = context.getEntity();
        Person oldPerson = context.getOldEntity();

        return person.isActive()
                && (oldPerson == null
                    || !Objects.equals(person.getStatus(), oldPerson.getStatus()));
    }
}
```

```java
@TentacolousListener(
    entity = Person.class,
    action = ActionListener.UPDATE,
    filter = ActivePersonFilter.class
)
public void onActivePersonUpdated(Person newPerson, Person oldPerson) {
}
```

### Available on every annotation

`@TentacolousListener` is optional. Existing annotations remain available and now accept the same custom filter:

```java
@UponInserting(entity = Person.class, filter = ActivePersonFilter.class)
public void onPersonInserted(Person person) {
}

@UponUpdating(entity = Person.class, filter = ActivePersonFilter.class)
public void onPersonUpdated(Person newPerson, Person oldPerson) {
}

@UponDeleting(entity = Person.class, filter = ActivePersonFilter.class)
public void onPersonDeleted(Person person) {
}
```

| Annotation | Operation | Custom filter |
| --- | --- | --- |
| `@UponInserting` | INSERT | Supported |
| `@UponUpdating` | UPDATE | Supported |
| `@UponDeleting` | DELETE | Supported |
| `@TentacolousListener` | Selected with `action` | Supported |

`getEntity()` contains the current payload. `getOldEntity()` is available for updates and is `null` for inserts and deletes. The context also exposes `getOperation()`, `getEventId()`, `getEntityName()`, and `getRecordKey()`.

For updates, `getChangedFields()` returns the changed JSON field names and `hasChanged("status")` checks one field. Inserts and deletes return an empty set and `false`.

When `filter` is configured together with `field`, `valueType`, or `value`, Tentacolous logs a warning. The custom filter has priority and the declarative filter is not executed. Filter errors follow the normal event retry flow.

## Invalid combinations {#invalid}

These combinations should fail when the application starts.

### Missing field

```java
@UponInserting(
    entity = Person.class,
    valueType = ValueType.BOOLEAN,
    value = "true"
)
public void invalid(Person person) {
}
```

### Missing value

```java
@UponInserting(
    entity = Person.class,
    field = "active",
    valueType = ValueType.BOOLEAN
)
public void invalid(Person person) {
}
```

### Missing valueType

```java
@UponUpdating(
    entity = Person.class,
    field = "name",
    value = "Anthony"
)
public void invalid(Person person) {
}
```

Use the complete declarative filter instead:

```java
@UponUpdating(
    entity = Person.class,
    field = "name",
    valueType = ValueType.STRING,
    value = "Anthony"
)
public void valid(Person person) {
}
```

### Same operation declared twice

```java
@UponUpdating(entity = Person.class)
@TentacolousListener(entity = Person.class, action = ActionListener.UPDATE)
public void invalid(Person person) {
}
```

A method can listen to different operations, but not to the same operation twice.

### Method with more than one parameter

```java
@UponInserting(entity = Person.class)
public void invalid(Person person, String other) {
}
```

### Incompatible parameter

```java
@UponInserting(entity = Person.class)
public void invalid(String person) {
}
```

## Manual testing {#manual-testing}

Assume this entity and listener:

```java
@Entity
@Table(name = "person")
public class Person {
    // ...
}

@UponInserting(entity = Person.class)
public void inserted(Person person) {
    System.out.println("Person inserted: " + person.getEmail());
}
```

Start your Spring Boot application. Tentacolous should print logs similar to:

```text
Tentacolous registered 1 listener method(s)
Initializing Tentacolous schema using event table 'db_change_event'
Creating Tentacolous INSERT trigger for table 'person' and entity 'Person'
Starting Tentacolous poller
```

Then run this SQL in PostgreSQL:

```sql
INSERT INTO public.person (email, lastname, "name")
VALUES ('test@example.com', 'Perez', 'Ana');
```

You should see this in your application console:

```java
Person inserted: test@example.com
```

Inspect events and triggers with:

```sql
select *
from db_change_event
order by id desc;

select trigger_name, event_object_schema, event_object_table
from information_schema.triggers
where event_object_table = 'person';
```

## How it works internally {#internals}

Tentacolous does not use JPA events. This is intentional: JPA events only work when the change goes through the same application. Tentacolous is designed to detect real database changes, even when they come from outside your application.

In PostgreSQL, Tentacolous creates:

- A `db_change_event` table.
- A `db_change_event_notify_change()` function.
- One trigger per table and operation.

The trigger stores `entity_name`, `operation`, `payload`, `old_payload`, `record_key`, `status`, `attempts`, `last_error` and processing timestamps.

`payload` contains the current row for inserts and updates, and the deleted row for deletes. `old_payload` contains the previous row only for updates. `record_key` identifies the affected record, using the entity `@Id` column when available or `id` otherwise.

When a listener asks for history, Tentacolous queries previous `INSERT` and `UPDATE` payloads for the same `entity_name` and `record_key`, ordered from oldest to newest.

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
- If you manage schema manually, include the `old_payload` and `record_key` columns and pass the record key field to the trigger function.
- Monitor `FAILED` events.
- Clean up or archive `db_change_event`.
- Avoid slow logic inside listeners.
- Publish to a queue if the process is heavy.

## Running Tentacolous tests {#tests}

From the library folder:

```shell
mvn test
```

Expected result:

```text
BUILD SUCCESS
```
