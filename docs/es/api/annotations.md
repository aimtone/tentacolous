## Parametros de anotaciones {#annotations}

Las anotaciones especificas y `@TentacolousListener(...)` comparten la misma configuracion de listener.

| Parametro | Requerido | Descripcion |
| --- | --- | --- |
| `entity` | Si | Clase de entidad que representa la tabla y recibe el payload deserializado. |
| `action` | Solo para `@TentacolousListener` | `ActionListener.INSERT`, `ActionListener.UPDATE` o `ActionListener.DELETE`. |
| `entityName` | No | Nombre logico del evento. Si se omite, Tentacolous usa el nombre simple de la clase. |
| `field` | Solo con filtros | Campo del payload que se quiere comparar. |
| `valueType` | Solo con filtros | Tipo usado para interpretar `value`. |
| `value` | Solo con filtros | Valor esperado, siempre escrito como texto. |
| `filter` | No | Bean de Spring que extiende `TentacolousFilter<T>`. Reemplaza el filtro declarativo. |
| `order` | No | Orden de ejecucion del listener. Los valores menores se ejecutan primero; el valor por defecto es `0`. |
| `exclude` | No | Columnas que no deben almacenarse en el payload del evento. |

### Orden de listeners

Usa `order` cuando varios listeners procesan la misma entidad y operacion. El orden funciona entre anotaciones especificas y genericas.

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

Si un listener falla, los siguientes no se ejecutan y el evento sigue el flujo normal de reintentos. Los efectos de los listeners deben ser idempotentes.

### Sobre entityName

La mayoria de las veces no necesitas `entityName`. Por defecto, `@UponInserting(entity = Person.class)` usa `Person`. Ese valor se almacena en `db_change_event.entity_name` y se usa internamente para relacionar eventos con listeners.

Usa `entityName` solo en casos avanzados donde necesites un nombre logico estable, por ejemplo cuando infraestructura externa ya escribe eventos con un nombre especifico.

### Sobre exclude

`exclude` no filtra listeners. Evita que columnas especificas se almacenen en el payload JSON del evento.

```java
@UponInserting(
    entity = User.class,
    exclude = {"password", "token", "secret_key"}
)
public void onUserInserted(User user) {
}
```

Esto importa porque la tabla de eventos puede contener datos de negocio. Normalmente no quieres guardar secretos ahi.
