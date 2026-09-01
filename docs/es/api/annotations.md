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

## @TentacolousCapture

`@TentacolousCapture` es una anotacion a nivel de clase. Hace que Tentacolous cree el trigger de una tabla para que sus cambios lleguen a los [sinks de colas de mensajes](../concepts/message-brokers.md), sin necesidad de un metodo listener. Nunca invoca codigo Java.

```java
@Configuration
@TentacolousCapture(entity = Person.class)
@TentacolousCapture(entity = Order.class, actions = {ActionListener.INSERT, ActionListener.UPDATE})
public class TentacolousCaptureConfig {
}
```

| Parametro | Requerido | Descripcion |
| --- | --- | --- |
| `entity` | Si | Clase de entidad. El nombre de tabla y la clave se resuelven desde las anotaciones JPA, igual que en los listeners. |
| `actions` | No | Operaciones a capturar. Vacio significa `INSERT`, `UPDATE` y `DELETE`. |
| `entityName` | No | Nombre logico del evento. Por defecto, el nombre simple de la clase. |
| `exclude` | No | Columnas que se quitan del payload almacenado y reenviado. |
| `field` + `valueType` + `value` | No | Filtro declarativo. Solo se reenvian a los sinks los cambios donde el campo del payload es igual al valor. Se declaran los tres juntos. |
| `filter` | No | Bean de Spring que extiende `TentacolousFilter<T>`. Solo se reenvian los cambios que acepta. Gana sobre el filtro declarativo. |
| `order` | No | Pista de orden relativa a otras capturas y listeners. |

Los parametros de filtro deciden **que cambios llegan a los sinks**; no invocan ningun metodo Java. Ver [Colas de mensajes](../concepts/message-brokers.md).

Una tabla que ya tiene un listener no necesita `@TentacolousCapture`; el trigger ya existe.

### Bean `Capture` — el equivalente programatico

Si prefieres no anotar una clase (posiblemente vacia), declara un bean `Capture`. Expone las mismas opciones como builder fluido y se recoge automaticamente:

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

| Anotacion | Bean `Capture` |
| --- | --- |
| `actions` | `.operations(...)` / `.operation(...)` |
| `entityName` | `.entityName(...)` |
| `exclude` | `.exclude(...)` |
| `field` + `valueType` + `value` | `.where(field, valueType, value)` |
| `filter` (clase) | `.filter(instancia)` |
| `order` | `.order(...)` |

Ambos mecanismos alimentan el mismo registro y se pueden mezclar.
