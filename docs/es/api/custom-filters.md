## Filtros personalizados {#custom-filters}

El filtro programatico es la novedad principal de `0.1.8`. Define la regla una vez en una clase reutilizable y usala desde cualquier anotacion de listener. La clase del filtro debe ser un bean de Spring.

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

### Disponible en todas las anotaciones

`@TentacolousListener` es opcional. Las anotaciones anteriores siguen disponibles y ahora aceptan el mismo filtro personalizado:

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

| Anotacion | Operacion | Filtro personalizado |
| --- | --- | --- |
| `@UponInserting` | INSERT | Disponible |
| `@UponUpdating` | UPDATE | Disponible |
| `@UponDeleting` | DELETE | Disponible |
| `@TentacolousListener` | Seleccionada con `action` | Disponible |

`getEntity()` contiene el payload actual. `getOldEntity()` esta disponible en updates y es `null` en inserts y deletes. El contexto tambien ofrece `getOperation()`, `getEventId()`, `getEntityName()` y `getRecordKey()`.

En updates, `getChangedFields()` entrega los nombres de campos JSON modificados y `hasChanged("status")` consulta un campo. En inserts y deletes devuelve un conjunto vacio y `false`.

Cuando se configura `filter` junto con `field`, `valueType` o `value`, Tentacolous escribe un warning. El filtro personalizado tiene prioridad y el filtro declarativo no se ejecuta. Los errores del filtro siguen el flujo normal de reintentos.
