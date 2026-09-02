![Tentacolous](../../assets/logonegro.png){ .content-logo }

Documentacion de Tentacolous

# Guia tecnica

Todo lo necesario para instalar, configurar y operar Tentacolous en un proyecto Spring Boot.

Aprende como agregar la dependencia, configurar eventos y crear listeners Java que reaccionan a cambios reales en PostgreSQL, MySQL, MariaDB, SQL Server, Oracle o SQLite.

## Resumen {#overview}

Tentacolous es una libreria Spring Boot que ejecuta metodos Java cuando una tabla de base de datos recibe un `INSERT`, `UPDATE` o `DELETE`.

La diferencia importante es que Tentacolous reacciona a los cambios de la base de datos sin importar donde se originen.

**La version 0.2.0 añade ejecucion agnostica a la base de datos.** Tentacolous detecta el producto JDBC y selecciona el comportamiento especifico de tablas, triggers, JSON, paginacion e historial, conservando la API existente de listeners y filtros.

**La version 0.3.0 añade sinks para colas de mensajes.** El mismo evento se puede reenviar a Kafka o RabbitMQ mediante un `ChangeEventSink`, y `@TentacolousCapture` transmite una tabla sin metodo listener. Ver [Colas de mensajes](#message-brokers).

**1.** Crea una tabla de eventos.

**2.** Crea infraestructura de triggers especifica para el motor.

**3.** Crea triggers para las tablas que tienen listeners.

**4.** Los triggers escriben eventos en `db_change_event`.

**5.** Un poller Spring lee esos eventos.

**6.** Tentacolous convierte el payload JSON en tu entidad Java.

**7.** Tentacolous ejecuta el metodo anotado.

**8.** Opcionalmente, Tentacolous reenvia el mismo cambio a una cola de mensajes (Kafka, RabbitMQ). Ver [Colas de mensajes](#message-brokers).

## Requisitos {#requirements}

- Java 17 o superior.
- Spring Boot.
- Una aplicacion Spring Boot con un `DataSource` configurado.
- PostgreSQL, MySQL, MariaDB, SQL Server, Oracle o SQLite, junto con su driver JDBC.

La infraestructura automatica esta disponible para PostgreSQL, MySQL, MariaDB, SQL Server, Oracle y SQLite.

## Snippets de dependencias {#dependency}

### Gradle

```groovy
implementation 'io.github.aimtone:tentacolous:0.3.0'
```

### Maven

```xml
<dependency>
  <groupId>io.github.aimtone</groupId>
  <artifactId>tentacolous</artifactId>
  <version>0.3.0</version>
</dependency>
```

## Migracion a 0.2.0 {#migration}

La version `0.2.0` mantiene compatibilidad con los listeners existentes. Los usuarios PostgreSQL no necesitan cambiar anotaciones; los demas motores requieren su driver JDBC y permisos de triggers cuando la gestion automatica esta habilitada.

- Usa `@TentacolousListener` solo si prefieres la anotacion generica con `action`.
- Los filtros personalizados son beans opcionales de Spring que extienden `TentacolousFilter<T>`.
- El tipo generico del filtro debe ser compatible con la entidad del listener.
- Si un filtro personalizado se declara junto con `field`, `valueType` o `value`, el filtro personalizado tiene prioridad y Tentacolous escribe un warning.
- Los filtros declarativos deben definir `field`, `valueType` y `value` juntos.
- Cada metodo puede declarar una anotacion listener por operacion.

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

Si la entidad tiene `@Table(name = "person")`, Tentacolous escucha la tabla `person`. Si no tiene `@Table`, Tentacolous infiere el nombre de tabla desde el nombre de clase.

| Entidad | Tabla inferida |
| --- | --- |
| `Person` | `person` |
| `UserAccount` | `user_account` |
| `PaymentTransaction` | `payment_transaction` |

Por eso las anotaciones no tienen un parametro `table`: la entidad seleccionada ya representa la tabla.

## Listeners y operaciones {#operations}

Comienza con las anotaciones especificas por operacion. Siguen completamente disponibles en 0.2.0 y ahora tambien aceptan filtros personalizados. El listener generico se explica despues como una alternativa opcional.

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

## Tipos de filtros {#filters}

Los filtros siempre usan esta estructura:

```text
field = "fieldName",
valueType = ValueType.TYPE,
value = "expected value"
```

`field`, `valueType` y `value` deben declararse juntos. Si falta uno, Tentacolous falla durante el escaneo de listeners y muestra un ejemplo de la forma valida.

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

## Filtros personalizados {#custom-filters}

El filtro programatico es la novedad principal de `0.2.0`. Define la regla una vez en una clase reutilizable y usala desde cualquier anotacion de listener. La clase del filtro debe ser un bean de Spring.

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

## Combinaciones invalidas {#invalid}

Estas combinaciones deberian fallar cuando inicia la aplicacion.

### Falta field

```java
@UponInserting(
    entity = Person.class,
    valueType = ValueType.BOOLEAN,
    value = "true"
)
public void invalid(Person person) {
}
```

### Falta value

```java
@UponInserting(
    entity = Person.class,
    field = "active",
    valueType = ValueType.BOOLEAN
)
public void invalid(Person person) {
}
```

### Falta valueType

```java
@UponUpdating(
    entity = Person.class,
    field = "name",
    value = "Anthony"
)
public void invalid(Person person) {
}
```

Usa el filtro declarativo completo:

```java
@UponUpdating(
    entity = Person.class,
    field = "name",
    valueType = ValueType.STRING,
    value = "Anthony"
)
public void valid(Person person) {
}
```

### Misma operacion declarada dos veces

```java
@UponUpdating(entity = Person.class)
@TentacolousListener(entity = Person.class, action = ActionListener.UPDATE)
public void invalid(Person person) {
}
```

Un metodo puede escuchar operaciones distintas, pero no la misma operacion dos veces.

### Metodo con mas de un parametro

```java
@UponInserting(entity = Person.class)
public void invalid(Person person, String other) {
}
```

### Parametro incompatible

```java
@UponInserting(entity = Person.class)
public void invalid(String person) {
}
```

## Pruebas manuales {#manual-testing}

Asume esta entidad y este listener:

```java
@Entity
@Table(name = "person")
public class Person {
    // ...
}

@UponInserting(entity = Person.class)
public void inserted(Person person) {
    System.out.println("Person inserted: " + person.getEmail());
}
```

Inicia tu aplicacion Spring Boot. Tentacolous deberia imprimir logs similares a:

```text
Tentacolous registered 1 listener method(s)
Initializing Tentacolous schema using event table 'db_change_event'
Creating Tentacolous INSERT trigger for table 'person' and entity 'Person'
Starting Tentacolous poller
```

Luego ejecuta este SQL en PostgreSQL:

```sql
INSERT INTO public.person (email, lastname, "name")
VALUES ('test@example.com', 'Perez', 'Ana');
```

Deberias ver esto en la consola de tu aplicacion:

```java
Person inserted: test@example.com
```

Inspecciona eventos y triggers con:

```sql
select *
from db_change_event
order by id desc;

select trigger_name, event_object_schema, event_object_table
from information_schema.triggers
where event_object_table = 'person';
```

## Como funciona internamente {#internals}

Tentacolous no usa eventos JPA. Esto es intencional: los eventos JPA solo funcionan cuando el cambio pasa por la misma aplicacion. Tentacolous esta disenado para detectar cambios reales de base de datos, incluso cuando vienen desde fuera de tu aplicacion.

En PostgreSQL, Tentacolous crea:

- Una tabla `db_change_event`.
- Una funcion `db_change_event_notify_change()`.
- Un trigger por tabla y operacion.

El trigger almacena `entity_name`, `operation`, `payload`, `old_payload`, `record_key`, `status`, `attempts`, `last_error` y timestamps de procesamiento.

`payload` contiene la fila actual para inserts y updates, y la fila eliminada para deletes. `old_payload` contiene la fila anterior solo para updates. `record_key` identifica el registro afectado, usando la columna `@Id` de la entidad cuando existe o `id` en caso contrario.

Cuando un listener pide historico, Tentacolous consulta los payloads anteriores de `INSERT` y `UPDATE` para el mismo `entity_name` y `record_key`, ordenados desde el mas antiguo al mas reciente.

El poller busca eventos `PENDING`, los marca como `PROCESSING`, ejecuta el listener y finalmente los marca como `PROCESSED`. Si ocurre un error, Tentacolous guarda `last_error` y reintenta hasta alcanzar `tentacolous.max-attempts`.

## Colas de mensajes {#message-brokers}

Además de invocar métodos listener, Tentacolous puede reenviar cada cambio a una cola de mensajes mediante un `ChangeEventSink`. Activar un sink convierte a Tentacolous en un relay ligero de *change data capture*: sin Kafka Connect, sin acceso al WAL/binlog, solo con los triggers y la tabla de eventos que ya administra.

Tras ejecutar los listeners, el poller entrega el evento a cada sink registrado. Un fallo de un sink deja el evento `PENDING` y lo reintenta, así que la entrega es **al menos una vez**; los consumidores deduplican por `eventId`.

### Elegir qué tablas se reenvían

Un trigger existe solo para una tabla con al menos un listener **o** una declaración de captura. Para transmitir una tabla sin ningún método `@Upon...`, declara una captura de una de dos formas equivalentes.

**Con la anotación** (el cuerpo de la clase puede quedar vacío):

```java
@Configuration
@TentacolousCapture(entity = Person.class)
@TentacolousCapture(entity = Order.class, actions = {ActionListener.INSERT, ActionListener.UPDATE})
public class TentacolousCaptureConfig {
}
```

**Con un bean `Capture`** (nada que instanciar — Spring ejecuta el método `@Bean` y Tentacolous lo recoge):

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
}
```

Ambas soportan `actions`/`.operations`, `entityName`, `exclude`, filtro declarativo (`field`/`valueType`/`value` o `.where(...)`), un `TentacolousFilter` programático (`filter` o `.filter(...)`) y `order`. El filtro de una captura decide **qué cambios llegan a los sinks**; un listener sin captura explícita sigue reenviando todo. Ver [Colas de mensajes](../concepts/message-brokers.md).

### Formato del mensaje

| `format` | Cuerpo |
| --- | --- |
| `envelope` (por defecto) | JSON con `eventId`, `entity`, `operation`, `recordKey`, `observedAt`, `before`, `after` |
| `raw` | El payload de la fila tal cual lo produjo el trigger |

Para `INSERT`, `before` es `null`. Para `DELETE`, `after` es `null` y `before` contiene la fila eliminada. Los headers `tentacolous-event-id`, `tentacolous-entity` y `tentacolous-operation` siempre están presentes.

### Kafka

```xml
<dependency>
  <groupId>org.springframework.kafka</groupId>
  <artifactId>spring-kafka</artifactId>
</dependency>
```

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092

tentacolous:
  kafka:
    enabled: true
    topic-prefix: "cdc."      # topic = cdc.<entityName>
    format: envelope
```

La clave del registro es el `recordKey`, así que todos los cambios de una fila mantienen su orden en la misma partición. El productor usa `acks=all` y `enable.idempotence=true` por defecto; sobrescribe con `spring.kafka.producer.*`.

```java
@KafkaListener(topics = "cdc.Person", groupId = "billing")
public void onPersonChange(ConsumerRecord<String, byte[]> record) {
    String json = new String(record.value(), StandardCharsets.UTF_8);
    // parsea el envelope, deduplica con el header tentacolous-event-id
}
```

### RabbitMQ

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

```yaml
spring:
  rabbitmq:
    host: localhost
    username: guest
    password: guest

tentacolous:
  rabbitmq:
    enabled: true
    exchange: tentacolous
    format: envelope
```

Los mensajes van a un topic exchange durable con routing key `entity.operation` (`person.update`, `order.delete`). Enlaza tus colas con el patrón que necesites:

```java
@Bean
Binding billingBinding(Queue billingQueue, TopicExchange tentacolousRabbitExchange) {
    return BindingBuilder.bind(billingQueue).to(tentacolousRabbitExchange).with("person.*");
}

@RabbitListener(queues = "billing.person")
public void onPersonChange(Message message) {
    // message.getBody() es el envelope JSON
}
```

### Sink propio

```java
@Component
public class WebhookSink implements ChangeEventSink {

    @Override
    public String name() {
        return "webhook";
    }

    @Override
    public boolean supports(ChangeEvent event) {
        return "Person".equals(event.getEntityName());
    }

    @Override
    public void publish(ChangeEvent event) throws Exception {
        // reenvia event.getPayload(); bloquea hasta el acuse
    }
}
```

Un `order()` más bajo se ejecuta antes que los sinks de la cola.

## Preguntas de arquitectura {#architecture-faq}

Respuestas condensadas a las preguntas que mas plantean los arquitectos. La version
completa, con tablas de compromisos y todos los modos de fallo, esta en la pagina
[Preguntas de arquitectura](../concepts/architecture-faq.md).

### ¿Por que usar esto en lugar de Debezium?

Debezium lee el log de transacciones y necesita Kafka Connect mas privilegios de log en la
base. Tentacolous es una libreria: triggers SQL escriben una fila de outbox en la
transaccion de negocio, un poller Spring la lee, y tus metodos anotados corren en el mismo
proceso. Elige Tentacolous para reacciones en proceso ante escrituras externas a volumen
moderado sin infraestructura extra; elige Debezium para CDC de alto volumen y fidelidad de
log (incluyendo `TRUNCATE` y fronteras de transaccion) con consumidores poliglotas.
Tambien pueden coexistir.

### ¿Que garantias tengo?

- **Sin eventos perdidos.** La fila de evento se escribe en la misma transaccion que el
  cambio de negocio: presente si hace commit, ausente si hace rollback (outbox
  transaccional).
- **Al-menos-una-vez** a listeners y sinks, con reintento hasta `max-attempts`.
- **Orden por registro.** Los eventos se procesan en orden de `id`; el sink de Kafka usa
  el record key como clave, asi que una particion mantiene ese orden.
- **No exactly-once.** Listeners y consumidores deben ser idempotentes; deduplica por
  `eventId`.
- **Despacho a-lo-sumo-una-vez por ciclo.** Reclamar es un
  `UPDATE ... WHERE id = ? AND status = 'PENDING'` atomico, asi que dos instancias no
  pueden despachar el mismo evento.

### ¿Que ocurre si el listener falla?

La excepcion se captura y se registra con el id del evento, entidad, operacion y metodo;
se guardan `last_error` y `attempts`; la fila vuelve a `PENDING` para reintento. Tras
`max-attempts` pasa a `FAILED` y queda para que la inspecciones y reproceses — las filas
`FAILED` son la tabla de dead-letter. Si varios listeners comparten entidad + operacion y
uno lanza excepcion, el despacho se detiene y el evento completo se reintenta, asi que todo
listener debe ser idempotente. Un crash tras reclamar pero antes de `PROCESSED` deja la
fila en `PROCESSING`; el poller no recolecta filas obsoletas automaticamente, asi que
monitorea `processing_started_at` y reinicialas.

### ¿Que pasa con Kafka?

Opcional y desactivado por defecto; los listeners funcionan sin broker. Al habilitarlo, el
poller reenvia cada evento a todos los `ChangeEventSink` despues de los listeners en
proceso, dentro del mismo esquema de reintentos — un outbox transaccional al broker sin
Kafka Connect. Los records llevan como clave el record key para orden por fila; la entrega
es al-menos-una-vez y los consumidores deduplican por `tentacolous-event-id`. Implementa
`ChangeEventSink` para cualquier otro transporte.

### ¿Puedo capturar tablas sin escribir codigo?

Si. Declara una anotacion `@TentacolousCapture` o un bean `Capture` y el cambio se reenvia
a los sinks sin un metodo listener Java, con el mismo selector de operaciones, `exclude` y
filtros que las anotaciones de listener. Un capture igual necesita una declaracion de
entidad/tabla — Tentacolous no escanea tablas no declaradas.

### ¿Como escala?

Por instancia, ajusta `poll-interval` (latencia) y `batch-size` (throughput), y sobre todo
manten los listeners rapidos: el procesamiento es de un solo hilo y secuencial por
instancia. Varias instancias pueden correr contra la misma base con seguridad (claim
atomico), pero no hay work-stealing, asi que escala a lo ancho con un sink de Kafka en vez
de agregar pollers. El diseno apunta a unos pocos miles de eventos por segundo por
instancia; mas alla de eso usa CDC basado en log. Contempla una escritura de fila extra
por cambio capturado y programa el archivado de filas `PROCESSED`.

## Seguridad {#security}

- No guardes secretos en el payload.
- Usa `exclude` para columnas sensibles.
- Protege `db_change_event` con permisos adecuados de base de datos.
- En produccion, usa `schema-management=validate` o `schema-management=none`.
- Manten los listeners idempotentes porque pueden ocurrir reintentos.

## Produccion {#production}

- Crea la tabla, funcion y triggers usando migraciones controladas.
- Usa `tentacolous.schema-management=validate` para verificar la infraestructura.
- Si administras el schema manualmente, incluye las columnas `old_payload` y `record_key`, y pasa el campo de clave del registro a la funcion del trigger.
- Monitorea eventos `FAILED`.
- Limpia o archiva `db_change_event`.
- Evita logica lenta dentro de los listeners.
- Publica a una cola si el proceso es pesado. Usa el [sink de Kafka o RabbitMQ](#message-brokers) integrado en lugar de hacerlo a mano dentro de un listener.
- Manten idempotentes a los consumidores de los sinks: la entrega es al menos una vez, con clave `eventId`.

## Ejecutar tests de Tentacolous {#tests}

Desde la carpeta de la libreria:

```shell
mvn test
```

Resultado esperado:

```text
BUILD SUCCESS
```
