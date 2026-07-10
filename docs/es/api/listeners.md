## Listeners y operaciones {#operations}

Comienza con las anotaciones especificas por operacion. Siguen completamente disponibles en 0.1.8 y ahora tambien aceptan filtros personalizados. El listener generico se explica despues como una alternativa opcional.

### UponInserting

`@UponInserting` ejecuta un metodo cuando ocurre un `INSERT` en la tabla de la entidad.

```java
@UponInserting(entity = Person.class)
public void onPersonInserted(Person person) {
    System.out.println("Inserted: " + person.getEmail());
}
```

Casos de uso: ejecutar logica despues de crear un registro, enviar una notificacion, iniciar un flujo o crear un registro de auditoria.

#### Insert con nombre logico explicito

```java
@UponInserting(entity = Person.class, entityName = "Person")
public void onPersonInserted(Person person) {
}
```

#### Insert con filtro STRING

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

#### Insert con filtro BOOLEAN

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

#### Insert con filtro numerico

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

#### Insert con filtro de fecha

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

#### Insert con columnas excluidas

```java
@UponInserting(
    entity = User.class,
    exclude = {"password", "token"}
)
public void onUserInserted(User user) {
}
```

### UponUpdating

`@UponUpdating` ejecuta un metodo cuando ocurre un `UPDATE` en la tabla de la entidad.

```java
@UponUpdating(entity = Person.class)
public void onPersonUpdated(Person person) {
    System.out.println("Updated: " + person.getEmail());
}
```

Tambien puedes recibir los valores anteriores del registro agregando un segundo parametro del mismo tipo de entidad.

```java
@UponUpdating(entity = Person.class)
public void onPersonUpdated(Person newPerson, Person oldPerson) {
}
```

Si agregas un tercer parametro, Tentacolous entrega los snapshots anteriores de ese mismo registro, ordenados desde el mas antiguo al mas reciente.

```java
@UponUpdating(entity = Person.class)
public void onPersonUpdated(Person newPerson, Person oldPerson, List<Person> history) {
}
```

El parametro de historico puede ser `List<Person>` o `Person[]`. Tentacolous relaciona el registro por su columna `@Id` cuando existe, o por `id` en caso contrario.

Casos de uso: recalcular datos derivados, invalidar cache, notificar a otros sistemas o sincronizar con un servicio externo.

#### Update con filtro STRING

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

#### Update con filtro BOOLEAN

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

#### Update con filtro DATETIME

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

#### Update con columnas excluidas

```java
@UponUpdating(
    entity = User.class,
    exclude = {"password", "refresh_token"}
)
public void onUserUpdated(User user) {
}
```

Las columnas excluidas se eliminan tanto del payload nuevo como del payload anterior.

### UponDeleting

`@UponDeleting` ejecuta un metodo cuando ocurre un `DELETE` en la tabla de la entidad. Para un delete, el payload contiene los valores previos del registro porque la fila ya no existe despues de eliminarse.

```java
@UponDeleting(entity = Person.class)
public void onPersonDeleted(Person person) {
    System.out.println("Deleted: " + person.getEmail());
}
```

Tambien puedes recibir los snapshots anteriores de ese mismo registro agregando un segundo parametro.

```java
@UponDeleting(entity = Person.class)
public void onPersonDeleted(Person person, List<Person> history) {
}
```

El parametro de historico puede ser `List<Person>` o `Person[]`.

Casos de uso: limpiar recursos externos, eliminar datos relacionados en otra base de datos, notificar eliminaciones o registrar auditoria de borrado.

#### Delete con filtro STRING

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

#### Delete con filtro UUID

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

#### Delete con columnas excluidas

```java
@UponDeleting(
    entity = User.class,
    exclude = {"password", "token"}
)
public void onUserDeleted(User user) {
}
```

### Alternativa generica: TentacolousListener

`@TentacolousListener` es la alternativa generica. Su parametro `action` determina la operacion y la firma valida del metodo.

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

Las reglas de parametros son identicas a las anotaciones especificas correspondientes.
