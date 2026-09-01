# Colas de mensajes (Kafka y RabbitMQ)

Tentacolous puede reenviar cada cambio detectado en la base de datos hacia una cola de mensajes,
además de (o en lugar de) invocar métodos listener en proceso. Esto convierte a Tentacolous en un
relay ligero de *change data capture*: sin Kafka Connect, sin acceso al WAL/binlog, solo con los
triggers y la tabla de eventos que ya tienes.

## Cómo funciona

```
cambio en la BD  ->  el trigger escribe db_change_event  ->  el poller lo lee
                                                                |
                                        +-----------------------+-----------------------+
                                        |                                               |
                              métodos @Upon...                                 ChangeEventSink(s)
                                                                              (Kafka, RabbitMQ, ...)
```

Cada bean `ChangeEventSink` se invoca una vez por evento, después de los métodos listener. Si un
sink lanza una excepción, el evento **no** se marca como procesado y sigue el flujo normal de
reintentos (`tentacolous.max-attempts`). La entrega es, por tanto, **al menos una vez**: el
consumidor debe tolerar recibir el mismo evento dos veces. Usa `eventId` (único y creciente) como
clave de deduplicación.

## Elegir qué tablas se reenvían

Un trigger solo existe para una tabla que tenga al menos un listener **o** una declaración de
captura. Si solo quieres transmitir una tabla y no tienes ningún método `@Upon...` para ella,
declara una captura. Hay dos formas equivalentes; usa la que encaje con tu código.

### Forma 1 — la anotación `@TentacolousCapture`

Se pone en cualquier clase administrada por Spring (el cuerpo puede quedar vacío):

```java
@Configuration
@TentacolousCapture(entity = Person.class)
@TentacolousCapture(
    entity = Order.class,
    actions = {ActionListener.INSERT, ActionListener.UPDATE},
    field = "status", valueType = ValueType.STRING, value = "APPROVED",
    exclude = {"internal_notes"})
public class TentacolousCaptureConfig {
}
```

### Forma 2 — un bean `Capture`

Un método `@Bean` por captura — la clase nunca queda vacía (SonarQube no se queja) y la referencia
a la entidad se verifica en compilación:

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

    // un filtro programático, inyectado como bean
    @Bean
    Capture activePersonCapture(ActivePersonFilter filter) {
        return Capture.of(Person.class).filter(filter);
    }
}
```

No instancias nada tú — Spring ejecuta el método `@Bean` al arrancar y Tentacolous recoge todos los
beans `Capture` automáticamente.

### ¿Cuál usar?

| | `@TentacolousCapture` | Bean `Capture` |
|---|---|---|
| La clase puede estar vacía | Sí (a algunos linters no les gusta) | No — tiene métodos `@Bean` |
| Entidad verificada en compilación | Sí | Sí |
| Condicional por perfil / property | No | Sí (`@Bean @Profile(...)`, `@ConditionalOnProperty`) |
| Filtro programático | `filter = MiFiltro.class` (se resuelve como bean) | `.filter(miFiltroBean)` (inyectado) |
| Testeable en aislamiento | Necesita contexto | Sí, es un objeto normal |

Ambas alimentan el mismo registro; puedes mezclarlas. `entity` se resuelve a nombre de tabla y
clave igual que en un listener (`@Table` / `@Id` de JPA, o el nombre de la clase en snake_case).

### Opciones de la captura

| Anotación | Bean `Capture` | Significado |
|---|---|---|
| `actions` | `.operations(...)` / `.operation(...)` | Operaciones a capturar. Por defecto: las tres. |
| `entityName` | `.entityName(...)` | Nombre lógico del evento. Por defecto: nombre simple de la clase. |
| `exclude` | `.exclude(...)` | Columnas quitadas del payload almacenado y reenviado. |
| `field` + `valueType` + `value` | `.where(field, valueType, value)` | Filtro declarativo: reenvía solo los cambios donde el campo es igual al valor. |
| `filter` | `.filter(...)` | `TentacolousFilter<T>` programático: reenvía solo los cambios que acepta. Gana sobre el filtro declarativo. |
| `order` | `.order(...)` | Pista de orden. |

### Cómo afectan los filtros de captura a los sinks

Los filtros de una captura deciden **qué cambios llegan a los sinks** — no ejecutan ningún método
Java.

| Situación | Qué reciben los sinks |
|---|---|
| Entidad con listener `@Upon...`, sin captura | Todos los cambios (comportamiento por defecto) |
| Entidad con una captura con filtro | Solo los cambios que pasan el filtro |
| Entidad con listener **y** captura con filtro | El listener sigue corriendo con su propio filtro; los sinks reciben solo lo que la captura acepta |
| Varias capturas para la misma entidad + operación | Se reenvía si **alguna** acepta (OR) |

Un filtro personalizado recibe un `TentacolousFilterContext` con la entidad actual, la entidad
anterior (updates), `getChangedFields()` / `hasChanged(...)`, `getRecordKey()` y `getEventId()` — el
mismo contexto que un filtro de listener.

## Formato del mensaje

Ambos sinks comparten la opción `format`:

| `format`   | Cuerpo |
|------------|--------|
| `envelope` (por defecto) | JSON autodescriptivo con metadatos y `before` / `after` |
| `raw` | El payload de la fila tal cual lo produjo el trigger |

Ejemplo de envelope (`UPDATE`):

```json
{
  "eventId": 42,
  "entity": "Person",
  "operation": "UPDATE",
  "recordKey": "7",
  "observedAt": "2026-09-01T12:00:00Z",
  "before": { "id": 7, "name": "Ana" },
  "after":  { "id": 7, "name": "Ana Maria" }
}
```

Para `INSERT`, `before` es `null`. Para `DELETE`, `after` es `null` y `before` contiene la fila
eliminada.

Independientemente del formato, estos headers siempre están disponibles (salvo que uses
`add-headers: false`):

- `tentacolous-event-id`
- `tentacolous-entity`
- `tentacolous-operation`

## Kafka

Añade Spring for Apache Kafka a tu aplicación (Tentacolous lo declara como dependencia opcional):

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

Configúralo:

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092

tentacolous:
  kafka:
    enabled: true
    topic-prefix: "cdc."      # topic = cdc.<entityName>, p. ej. cdc.Person
    # topic: all-changes      # o fuerza un único topic fijo
    format: envelope
    add-headers: true
    send-timeout: 10s
```

- La **clave** del registro es `recordKey`, así que todos los cambios de la misma fila caen en la
  misma partición y mantienen el orden.
- El productor usa `acks=all` y `enable.idempotence=true` por defecto; puedes sobrescribirlo con
  las propiedades estándar `spring.kafka.producer.*`.
- `send-timeout` es cuánto espera el poller el acuse del broker antes de fallar el evento (se
  reintenta en el siguiente ciclo).

En el consumidor no hace falta nada especial:

```java
@KafkaListener(topics = "cdc.Person", groupId = "billing")
public void onPersonChange(ConsumerRecord<String, byte[]> record) {
    String json = new String(record.value(), StandardCharsets.UTF_8);
    // parsea el envelope, deduplica con el header tentacolous-event-id
}
```

## RabbitMQ

Añade Spring AMQP:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

Configúralo:

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

tentacolous:
  rabbitmq:
    enabled: true
    exchange: tentacolous          # topic exchange durable, declarado al arrancar
    routing-key-prefix: ""         # routing key = <prefix><entity>.<operation>, en minúsculas
    format: envelope
    declare-exchange: true
    add-headers: true
```

Los mensajes se publican en un **topic exchange** con routing key `entity.operation`, por ejemplo
`person.update` u `order.delete`. Enlaza tus colas con el patrón que necesites:

```java
@Bean
Queue billingQueue() {
    return QueueBuilder.durable("billing.person").build();
}

@Bean
Binding billingBinding(Queue billingQueue, TopicExchange tentacolousRabbitExchange) {
    return BindingBuilder.bind(billingQueue).to(tentacolousRabbitExchange).with("person.*");
}

@RabbitListener(queues = "billing.person")
public void onPersonChange(Message message) {
    // message.getBody() es el envelope JSON
}
```

Los mensajes son persistentes y llevan `tentacolous-event-id` como `messageId` de AMQP.

## Usar ambos a la vez

Activar `tentacolous.kafka.enabled` y `tentacolous.rabbitmq.enabled` juntos registra los dos
sinks. Cada evento se publica en ambos; si cualquiera falla, todo el evento se reintenta, así que
ambos transportes pueden ver una reentrega. Mantén los consumidores idempotentes.

## Crear tu propio sink

Implementa `ChangeEventSink` y exponlo como bean:

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
        // envía event.getPayload() a algún sitio; bloquea hasta el acuse
    }
}
```

Devuelve un `order()` más bajo para ejecutarte antes que los sinks de la cola.
