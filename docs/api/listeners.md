## Listeners and operations {#operations}

Start with the operation-specific annotations. They remain fully supported in 0.1.8 and now also accept custom filters. The generic listener is explained afterward as an optional alternative.

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
