# Tentacolous

Tentacolous is a Spring Boot library that runs Java methods when a database table receives an `INSERT`, `UPDATE`, or `DELETE`.

The important difference is that Tentacolous does not depend on the change happening inside your application. If another API, a script, DBeaver, a SQL console, or a legacy system modifies the database, Tentacolous can detect that change too.

Internally, Tentacolous uses database infrastructure:

1. It creates an event table.
2. It creates a PostgreSQL function.
3. It creates triggers for tables that have listeners.
4. The triggers write events into `db_change_event`.
5. A Spring poller reads those events.
6. Tentacolous converts the event JSON payload into your Java entity.
7. Tentacolous runs the annotated method.

## Compatibility

Latest stable version: `0.1.6`

Use `0.1.6` when you want the current stable Maven Central release.

## Requirements

- Java 17 or higher.
- Spring Boot.
- A Spring Boot application with a configured `DataSource`.
- PostgreSQL for automatic trigger creation.
- Maven or Gradle in the application that will consume Tentacolous.

Automatic database infrastructure creation is currently implemented for PostgreSQL.

## Installation From Maven Central

Tentacolous is published to Maven Central. Add the stable release directly to your Maven or Gradle project.

Latest stable artifact:

```text
io.github.aimtone:tentacolous:0.1.6
```

### Gradle

For most Gradle projects, make sure `mavenCentral()` is enabled:

```gradle
repositories {
    mavenCentral()
}
```

Then add Tentacolous:

```gradle
dependencies {
    implementation 'io.github.aimtone:tentacolous:0.1.6'
}
```

If your project uses `dependencyResolutionManagement` in `settings.gradle`, configure repositories there:

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}
```

### Maven

Add this dependency to your project's `pom.xml`:

```xml
<dependency>
  <groupId>io.github.aimtone</groupId>
  <artifactId>tentacolous</artifactId>
  <version>0.1.6</version>
</dependency>
```

## Application Configuration

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

Available properties:

| Property | Default | What it does |
| --- | --- | --- |
| `tentacolous.enabled` | `true` | Enables or disables Tentacolous. |
| `tentacolous.schema-management` | `auto` | Defines whether Tentacolous creates, validates, or ignores database infrastructure. |
| `tentacolous.event-table` | `db_change_event` | Name of the table where database change events are stored. |
| `tentacolous.poll-interval` | `1s` | How often pending events are read. |
| `tentacolous.initial-delay` | `0s` | Delay before the poller starts. |
| `tentacolous.batch-size` | `100` | Maximum number of events read in each polling cycle. |
| `tentacolous.max-attempts` | `3` | Retry limit before an event is marked as `FAILED`. |

`schema-management` modes:

| Mode | Common use | Behavior |
| --- | --- | --- |
| `auto` | Development | Creates the table, function, and triggers if needed. |
| `create` | Controlled development environments | Forces supported infrastructure creation. |
| `validate` | Production | Validates that the infrastructure exists, without creating it. |
| `none` | Production with migrations | Does not create or validate infrastructure. |

To get started, use:

```yaml
tentacolous:
  schema-management: auto
```

In production, `validate` or `none` is usually a better choice because creating triggers requires elevated database permissions.

## Example Entity

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

If the entity has:

```java
@Table(name = "person")
```

Tentacolous listens to the `person` table.

If the entity does not have `@Table`, Tentacolous infers the table name from the class name:

| Entity | Inferred table |
| --- | --- |
| `Person` | `person` |
| `UserAccount` | `user_account` |
| `PaymentTransaction` | `payment_transaction` |

That is why the annotations do not have a `table` parameter: the selected entity already represents the table.

## Annotation Parameters

The three annotations have the same shape:

```java
@UponInserting(...)
@UponUpdating(...)
@UponDeleting(...)
```

Parameters:

| Parameter | Required | Description |
| --- | --- | --- |
| `entity` | Yes | Entity class that represents the table and receives the deserialized event payload. |
| `entityName` | No | Logical event name. If omitted, Tentacolous uses the simple class name, for example `Person`. |
| `field` | Only with filters | Payload field that you want to compare. |
| `valueType` | Only with filters | Type used to interpret `value`. |
| `value` | Only with filters | Expected value, always written as text. |
| `exclude` | No | Columns that should not be stored in the event payload. |

### About `entityName`

Most of the time, you do not need `entityName`.

By default, if you write:

```java
@UponInserting(entity = Person.class)
```

Tentacolous uses:

```text
entityName = "Person"
```

That value is stored in `db_change_event.entity_name` and is used internally to match events with listeners.

`entityName` exists for advanced cases where you need a stable logical name. For example, you may already have manually created events or external infrastructure that uses a specific name. If you are just getting started, do not use it.

### About `exclude`

`exclude` does not filter listeners.

`exclude` only prevents specific columns from being stored in the event JSON payload. It is useful for sensitive data:

```java
@UponInserting(
    entity = User.class,
    exclude = {"password", "token", "secret_key"}
)
public void onUserInserted(User user) {
}
```

If the table has a `password` column, the trigger will not copy it into `db_change_event.payload`.

This matters because the event table may contain business data. You usually do not want secrets stored there.

## Filter Types

Filters always use this structure:

```java
field = "fieldName",
valueType = ValueType.TYPE,
value = "expected value"
```

Supported types:

| `ValueType` | `value` format | Example |
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

If you use `valueType`, you must also define `field` and `value`.

## UponInserting

`@UponInserting` runs a method when an `INSERT` occurs on the entity table.

### Basic Insert

```java
@UponInserting(entity = Person.class)
public void onPersonInserted(Person person) {
    System.out.println("Inserted: " + person.getEmail());
}
```

Use cases:

- Run logic after a record is created.
- Send a notification.
- Publish a Kafka message.
- Create an audit record.

### Insert With Explicit Logical Name

```java
@UponInserting(entity = Person.class, entityName = "Person")
public void onPersonInserted(Person person) {
}
```

This does the same as the basic example if your class is named `Person`. It is useful only when you need to control the value stored in `db_change_event.entity_name`.

### Insert With STRING Filter

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

This listener only runs if the payload contains:

```json
{
  "email": "admin@example.com"
}
```

### Insert With BOOLEAN Filter

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

This listener only runs when `active` is `true`.

### Insert With Numeric Filter

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

You can use `NUMBER`, `INTEGER`, `LONG`, `DECIMAL`, or `DOUBLE` depending on your data.

### Insert With Date Filter

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

### Insert With Excluded Columns

```java
@UponInserting(
    entity = User.class,
    exclude = {"password", "token"}
)
public void onUserInserted(User user) {
}
```

The listener still runs normally, but those columns are not stored in `db_change_event.payload`.

## UponUpdating

`@UponUpdating` runs a method when an `UPDATE` occurs on the entity table.

### Basic Update

```java
@UponUpdating(entity = Person.class)
public void onPersonUpdated(Person person) {
    System.out.println("Updated: " + person.getEmail());
}
```

You can also receive the previous record values by adding a second parameter of the same entity type:

```java
@UponUpdating(entity = Person.class)
public void onPersonUpdated(Person newPerson, Person oldPerson) {
}
```

If you add a third parameter, Tentacolous provides the previous snapshots for that same record, ordered from oldest to newest:

```java
@UponUpdating(entity = Person.class)
public void onPersonUpdated(Person newPerson, Person oldPerson, List<Person> history) {
}
```

The history parameter can be a `List<Person>` or `Person[]`. Tentacolous matches the record by its `@Id` column when available, otherwise by `id`.

Use cases:

- Recalculate derived data.
- Invalidate cache.
- Notify other systems.
- Synchronize with an external service.

### Update With STRING Filter

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

This listener only runs when the updated record ends with `status = "APPROVED"`.

### Update With BOOLEAN Filter

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

### Update With DATETIME Filter

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

### Update With Excluded Columns

```java
@UponUpdating(
    entity = User.class,
    exclude = {"password", "refresh_token"}
)
public void onUserUpdated(User user) {
}
```

Use this when the `UPDATE` may touch sensitive columns that you do not want stored in the event table.
Excluded columns are removed from both the new and previous payloads.

## UponDeleting

`@UponDeleting` runs a method when a `DELETE` occurs on the entity table.

For a `DELETE`, the payload contains the previous values of the record because the row no longer exists after deletion.

### Basic Delete

```java
@UponDeleting(entity = Person.class)
public void onPersonDeleted(Person person) {
    System.out.println("Deleted: " + person.getEmail());
}
```

You can also receive the previous snapshots for that same record by adding a second parameter:

```java
@UponDeleting(entity = Person.class)
public void onPersonDeleted(Person person, List<Person> history) {
}
```

The history parameter can be a `List<Person>` or `Person[]`.

Use cases:

- Clean up external resources.
- Delete related data in another database.
- Notify about deletions.
- Register deletion audit data.

### Delete With STRING Filter

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

This listener only runs if the deleted person had that email.

### Delete With UUID Filter

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

### Delete With Excluded Columns

```java
@UponDeleting(
    entity = User.class,
    exclude = {"password", "token"}
)
public void onUserDeleted(User user) {
}
```

This prevents sensitive values from the deleted row from being stored in `db_change_event.payload`.

## Invalid Combinations

These combinations should fail when the application starts.

Missing `field`:

```java
@UponInserting(
    entity = Person.class,
    valueType = ValueType.BOOLEAN,
    value = "true"
)
public void invalid(Person person) {
}
```

Missing `value`:

```java
@UponInserting(
    entity = Person.class,
    field = "active",
    valueType = ValueType.BOOLEAN
)
public void invalid(Person person) {
}
```

Method with more than one parameter:

```java
@UponInserting(entity = Person.class)
public void invalid(Person person, String other) {
}
```

Incompatible parameter:

```java
@UponInserting(entity = Person.class)
public void invalid(String person) {
}
```

## Manual Testing

Assume this entity:

```java
@Entity
@Table(name = "person")
public class Person {
    // ...
}
```

And this listener:

```java
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
INSERT INTO public.person ("email", "lastname", "name")
VALUES ('johndoe@example.com', 'Doe', 'John');
```

You should see this in your application console:

```text
Person inserted: test@example.com
```

You can inspect events:

```sql
select *
from db_change_event
order by id desc;
```

And triggers:

```sql
select trigger_name, event_object_schema, event_object_table
from information_schema.triggers
where event_object_table = 'person';
```

## How It Works Internally

Tentacolous does not use JPA events. This is intentional.

JPA events only work when the change goes through the same application. Tentacolous is designed to detect real database changes, even when they come from outside your application.

In PostgreSQL, Tentacolous creates:

- A `db_change_event` table.
- A `db_change_event_notify_change()` function.
- One trigger per table and operation.

The trigger stores:

- `entity_name`
- `operation`
- `payload`
- `status`
- `attempts`
- `last_error`
- processing timestamps

The poller looks for `PENDING` events, marks them as `PROCESSING`, runs the listener, and finally marks them as `PROCESSED`.

If an error happens, Tentacolous stores `last_error` and retries until `tentacolous.max-attempts` is reached.

## Security

- Do not store secrets in the payload.
- Use `exclude` for sensitive columns.
- Protect `db_change_event` with proper database permissions.
- In production, use `schema-management=validate` or `schema-management=none`.
- Keep listeners idempotent because retries may happen.

## Production

Recommendations:

- Create the table, function, and triggers using controlled migrations.
- Use `tentacolous.schema-management=validate` to verify infrastructure.
- Monitor `FAILED` events.
- Clean up or archive `db_change_event`.
- Avoid slow logic inside listeners.
- Publish to a queue if the process is heavy.

## Running Tentacolous Tests

From the library folder:

```bash
mvn test
```

Expected result:

```text
BUILD SUCCESS
```
