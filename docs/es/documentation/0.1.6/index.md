![Tentacolous](../../../assets/logonegro.png){ .content-logo }

Documentacion de Tentacolous

# Guia tecnica

Todo lo necesario para instalar, configurar y operar Tentacolous en un proyecto Spring Boot.

Esta pagina expande el README con una estructura mas navegable. Mantiene el alcance real del repositorio:
Tentacolous usa infraestructura PostgreSQL, una tabla de eventos y un poller Spring para ejecutar metodos Java anotados.

## Resumen {#overview}

Tentacolous es una libreria Spring Boot que ejecuta metodos Java cuando una tabla de base de datos recibe un `INSERT`, `UPDATE` o `DELETE`.

La diferencia importante es que Tentacolous reacciona a los cambios de la base de datos sin importar donde se originen.

**1.** Crea una tabla de eventos.

**2.** Crea una funcion PostgreSQL.

**3.** Crea triggers para las tablas que tienen listeners.

**4.** Los triggers escriben eventos en `db_change_event`.

**5.** Un poller Spring lee esos eventos.

**6.** Tentacolous convierte el payload JSON en tu entidad Java.

**7.** Tentacolous ejecuta el metodo anotado.

## Requisitos {#requirements}

- Java 17 o superior.
- Spring Boot.
- Una aplicacion Spring Boot con un `DataSource` configurado.
- PostgreSQL para la creacion automatica de triggers.

La creacion automatica de infraestructura de base de datos esta implementada actualmente para PostgreSQL.

## Dependencia de la version 0.1.6 {#install}

Usa estas coordenadas cuando tu aplicacion requiera Tentacolous 0.1.6.

```groovy
implementation 'io.github.aimtone:tentacolous:0.1.6'
```

```xml
<dependency>
  <groupId>io.github.aimtone</groupId>
  <artifactId>tentacolous</artifactId>
  <version>0.1.6</version>
</dependency>
```

## Configuracion de la aplicacion {#config}

Tentacolous necesita que tu aplicacion tenga una conexion de base de datos. Ejemplo de `application.yml`:

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

### Propiedades disponibles

| Propiedad | Valor por defecto | Que hace |
| --- | --- | --- |
| `tentacolous.enabled` | `true` | Activa o desactiva Tentacolous. |
| `tentacolous.schema-management` | `auto` | Define si Tentacolous crea, valida o ignora la infraestructura de base de datos. |
| `tentacolous.event-table` | `db_change_event` | Nombre de la tabla de eventos. |
| `tentacolous.poll-interval` | `1s` | Frecuencia con que se leen eventos pendientes. |
| `tentacolous.initial-delay` | `0s` | Demora antes de iniciar el poller. |
| `tentacolous.batch-size` | `100` | Numero maximo de eventos leidos por ciclo. |
| `tentacolous.max-attempts` | `3` | Limite de reintentos antes de marcar un evento como `FAILED`. |

### Modos de schema management

| Modo | Uso comun | Comportamiento |
| --- | --- | --- |
| `auto` | Desarrollo | Crea la tabla, funcion y triggers si hace falta. |
| `create` | Ambientes de desarrollo controlados | Fuerza la creacion de infraestructura soportada. |
| `validate` | Produccion | Valida que la infraestructura exista sin crearla. |
| `none` | Produccion con migraciones | No crea ni valida infraestructura. |

Para comenzar, usa `schema-management: auto`. En produccion, `validate` o `none` suele ser mejor porque crear triggers requiere permisos elevados en la base de datos.

## Entidad de ejemplo {#entity}

Tentacolous usa la entidad para saber que tabla de base de datos debe escuchar.

```java
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "persona")
public class Persona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String lastname;
    private String email;

    public Persona() {
    }

    // getters and setters
}
```

Si la entidad tiene `@Table(name = "persona")`, Tentacolous escucha la tabla `persona`. Si no tiene `@Table`, Tentacolous infiere el nombre de tabla desde el nombre de clase.

| Entidad | Tabla inferida |
| --- | --- |
| `Persona` | `persona` |
| `UserAccount` | `user_account` |
| `PaymentTransaction` | `payment_transaction` |

Por eso las anotaciones no tienen un parametro `table`: la entidad seleccionada ya representa la tabla.

## Parametros de anotaciones {#annotations}

Las tres anotaciones tienen la misma forma: `@UponInserting(...)`, `@UponUpdating(...)` y `@UponDeleting(...)`.

| Parametro | Requerido | Descripcion |
| --- | --- | --- |
| `entity` | Si | Clase de entidad que representa la tabla y recibe el payload deserializado. |
| `entityName` | No | Nombre logico del evento. Si se omite, Tentacolous usa el nombre simple de la clase. |
| `field` | Solo con filtros | Campo del payload que se quiere comparar. |
| `valueType` | Solo con filtros | Tipo usado para interpretar `value`. |
| `value` | Solo con filtros | Valor esperado, siempre escrito como texto. |
| `exclude` | No | Columnas que no deben almacenarse en el payload del evento. |

### Sobre entityName

La mayoria de las veces no necesitas `entityName`. Por defecto, `@UponInserting(entity = Persona.class)` usa `Persona`. Ese valor se almacena en `db_change_event.entity_name` y se usa internamente para relacionar eventos con listeners.

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

## Tipos de filtros {#filters}

Los filtros siempre usan esta estructura:

```text
field = "fieldName",
valueType = ValueType.TYPE,
value = "expected value"
```

Si usas `valueType`, tambien debes definir `field` y `value`.

| ValueType | Formato de value | Ejemplo |
| --- | --- | --- |
| `STRING` | Texto exacto | `"APPROVED"` |
| `BOOLEAN` | `true` or `false` | `"true"` |
| `NUMBER` | Numero entero largo | `"1"` |
| `INTEGER` | Numero entero | `"7"` |
| `LONG` | Numero entero largo | `"999"` |
| `DECIMAL` | Decimal exacto | `"10.50"` |
| `DOUBLE` | Decimal de punto flotante | `"3.14"` |
| `DATE` | Fecha ISO | `"2026-07-07"` |
| `TIME` | Hora ISO | `"13:45:00"` |
| `DATETIME` | Instant o datetime ISO | `"2026-07-07T13:45:00Z"` |
| `UUID` | UUID canonico | `"550e8400-e29b-41d4-a716-446655440000"` |

## Operaciones {#operations}

### UponInserting

`@UponInserting` ejecuta un metodo cuando ocurre un `INSERT` en la tabla de la entidad.

```java
@UponInserting(entity = Persona.class)
public void onPersonaInserted(Persona persona) {
    System.out.println("Inserted: " + persona.getEmail());
}
```

Casos de uso: ejecutar logica despues de crear un registro, enviar una notificacion, publicar un mensaje Kafka o crear un registro de auditoria.

#### Insert con nombre logico explicito

```java
@UponInserting(entity = Persona.class, entityName = "Persona")
public void onPersonaInserted(Persona persona) {
}
```

#### Insert con filtro STRING

```java
@UponInserting(
    entity = Persona.class,
    field = "email",
    valueType = ValueType.STRING,
    value = "admin@example.com"
)
public void onAdminInserted(Persona persona) {
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
@UponUpdating(entity = Persona.class)
public void onPersonaUpdated(Persona persona) {
    System.out.println("Updated: " + persona.getEmail());
}
```

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

### UponDeleting

`@UponDeleting` ejecuta un metodo cuando ocurre un `DELETE` en la tabla de la entidad. Para un delete, el payload contiene los valores previos del registro porque la fila ya no existe despues de eliminarse.

```java
@UponDeleting(entity = Persona.class)
public void onPersonaDeleted(Persona persona) {
    System.out.println("Deleted: " + persona.getEmail());
}
```

Casos de uso: limpiar recursos externos, eliminar datos relacionados en otra base de datos, notificar eliminaciones o registrar auditoria de borrado.

#### Delete con filtro STRING

```java
@UponDeleting(
    entity = Persona.class,
    field = "email",
    valueType = ValueType.STRING,
    value = "admin@example.com"
)
public void onAdminDeleted(Persona persona) {
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

## Combinaciones invalidas {#invalid}

Estas combinaciones deberian fallar cuando inicia la aplicacion.

### Falta field

```java
@UponInserting(
    entity = Persona.class,
    valueType = ValueType.BOOLEAN,
    value = "true"
)
public void invalid(Persona persona) {
}
```

### Falta value

```java
@UponInserting(
    entity = Persona.class,
    field = "active",
    valueType = ValueType.BOOLEAN
)
public void invalid(Persona persona) {
}
```

### Metodo con mas de un parametro

```java
@UponInserting(entity = Persona.class)
public void invalid(Persona persona, String other) {
}
```

### Parametro incompatible

```java
@UponInserting(entity = Persona.class)
public void invalid(String persona) {
}
```

## Pruebas manuales {#manual-testing}

Asume esta entidad y este listener:

```java
@Entity
@Table(name = "persona")
public class Persona {
    // ...
}

@UponInserting(entity = Persona.class)
public void inserted(Persona persona) {
    System.out.println("Persona inserted: " + persona.getEmail());
}
```

Inicia tu aplicacion Spring Boot. Tentacolous deberia imprimir logs similares a:

```text
Tentacolous registered 1 listener method(s)
Initializing Tentacolous schema using event table 'db_change_event'
Creating Tentacolous INSERT trigger for table 'persona' and entity 'Persona'
Starting Tentacolous poller
```

Luego ejecuta este SQL en PostgreSQL:

```sql
INSERT INTO public.persona (email, lastname, "name")
VALUES ('test@example.com', 'Perez', 'Ana');
```

Deberias ver esto en la consola de tu aplicacion:

```java
Persona inserted: test@example.com
```

Inspecciona eventos y triggers con:

```sql
select *
from db_change_event
order by id desc;

select trigger_name, event_object_schema, event_object_table
from information_schema.triggers
where event_object_table = 'persona';
```

## Como funciona internamente {#internals}

Tentacolous no usa eventos JPA. Esto es intencional: los eventos JPA solo funcionan cuando el cambio pasa por la misma aplicacion. Tentacolous esta disenado para detectar cambios reales de base de datos, incluso cuando vienen desde fuera de tu aplicacion.

En PostgreSQL, Tentacolous crea:

- Una tabla `db_change_event`.
- Una funcion `db_change_event_notify_change()`.
- Un trigger por tabla y operacion.

El trigger almacena `entity_name`, `operation`, `payload`, `status`, `attempts`, `last_error` y timestamps de procesamiento.

El poller busca eventos `PENDING`, los marca como `PROCESSING`, ejecuta el listener y finalmente los marca como `PROCESSED`. Si ocurre un error, Tentacolous guarda `last_error` y reintenta hasta alcanzar `tentacolous.max-attempts`.

## Seguridad {#security}

- No guardes secretos en el payload.
- Usa `exclude` para columnas sensibles.
- Protege `db_change_event` con permisos adecuados de base de datos.
- En produccion, usa `schema-management=validate` o `schema-management=none`.
- Manten los listeners idempotentes porque pueden ocurrir reintentos.

## Produccion {#production}

- Crea la tabla, funcion y triggers usando migraciones controladas.
- Usa `tentacolous.schema-management=validate` para verificar la infraestructura.
- Monitorea eventos `FAILED`.
- Limpia o archiva `db_change_event`.
- Evita logica lenta dentro de los listeners.
- Publica a una cola si el proceso es pesado.

## Ejecutar tests de Tentacolous {#tests}

Desde la carpeta de la libreria:

```shell
mvn test
```

Para limpiar, compilar, testear e instalar:

```shell
mvn clean install
```

Resultado esperado:

```text
BUILD SUCCESS
```

## Limitaciones actuales {#limitations}

- La creacion automatica de infraestructura actualmente esta implementada solo para PostgreSQL.
- Tentacolous no usa CDC nativo como Debezium o logical replication.
- El polling es simple. Para sistemas de volumen muy alto, CDC nativo o una cola especializada puede ser una mejor opcion.
