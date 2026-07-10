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
