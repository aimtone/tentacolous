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

## @TentacolousCapture

`@TentacolousCapture` is a type-level annotation. It makes Tentacolous create the database trigger for a table so its changes reach the configured [message-broker sinks](../concepts/message-brokers.md), without requiring a listener method. It never invokes Java code.

```java
@Configuration
@TentacolousCapture(entity = Person.class)
@TentacolousCapture(entity = Order.class, actions = {ActionListener.INSERT, ActionListener.UPDATE})
public class TentacolousCaptureConfig {
}
```

| Parameter | Required | Description |
| --- | --- | --- |
| `entity` | Yes | Entity class. Table name and record key are resolved from JPA annotations, as with listeners. |
| `actions` | No | Operations to capture. Empty means `INSERT`, `UPDATE` and `DELETE`. |
| `entityName` | No | Logical event name. Defaults to the class simple name. |
| `exclude` | No | Columns removed from the stored and forwarded payload. |
| `field` + `valueType` + `value` | No | Declarative filter. Only changes where the payload field equals the value are forwarded to the sinks. Declare the three together. |
| `filter` | No | Spring bean extending `TentacolousFilter<T>`. Only changes it accepts are forwarded. Takes precedence over the declarative filter. |
| `order` | No | Ordering hint relative to other captures and listeners. |

The filter parameters gate **which changes reach the message-broker sinks**; they do not invoke any Java method. See [Message brokers](../concepts/message-brokers.md).

A table that already has a listener does not need `@TentacolousCapture`; the trigger already exists.

### `Capture` bean — the programmatic equivalent

If you prefer not to annotate a (possibly empty) class, declare a `Capture` bean instead. It exposes the same options as a fluent builder and is collected automatically:

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

    @Bean
    Capture activePersonCapture(ActivePersonFilter filter) {
        return Capture.of(Person.class).filter(filter);
    }
}
```

| Annotation | `Capture` bean |
| --- | --- |
| `actions` | `.operations(...)` / `.operation(...)` |
| `entityName` | `.entityName(...)` |
| `exclude` | `.exclude(...)` |
| `field` + `valueType` + `value` | `.where(field, valueType, value)` |
| `filter` (class) | `.filter(instance)` |
| `order` | `.order(...)` |

Both mechanisms feed the same registry and can be mixed.
