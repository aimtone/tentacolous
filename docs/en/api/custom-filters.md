## Custom filters {#custom-filters}

Programmatic filtering is the main addition in `0.1.8`. Define the rule once in a reusable class and reference it from any listener annotation. The filter class must be a Spring bean.

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
