# Tentacolous 0.3.0 {#top}

La version `0.3.0` añade sinks para colas de mensajes. Cada cambio de base de datos detectado por
Tentacolous ahora se puede reenviar a Kafka o RabbitMQ, ademas de (o en lugar de) invocar metodos
listener en proceso. La API de listeners y filtros de `0.2.0` no cambia.

## Instalacion

```xml
<dependency>
  <groupId>io.github.aimtone</groupId>
  <artifactId>tentacolous</artifactId>
  <version>0.3.0</version>
</dependency>
```

Agrega `spring-kafka` o `spring-boot-starter-amqp` a tu aplicacion solo cuando actives el sink
correspondiente. Tentacolous declara ambas como dependencias opcionales.

## Novedades

- **SPI `ChangeEventSink`.** Cualquier bean de Spring que la implemente recibe cada cambio
  detectado despues de los metodos listener. La entrega es al menos una vez; un fallo del sink deja
  el evento `PENDING` y reutiliza el flujo de reintentos existente. Deduplica por `eventId`.
- **Sink de Kafka** (`tentacolous.kafka.*`). Un registro por evento, con clave por record key para
  mantener el orden por fila, con `acks=all` e idempotencia del productor por defecto.
- **Sink de RabbitMQ** (`tentacolous.rabbitmq.*`). Publica en un topic exchange durable con routing
  key `entity.operation`.
- **Formatos de mensaje.** `envelope` (por defecto, con `before` / `after` y metadata) o `raw`. Los
  headers `tentacolous-event-id`, `tentacolous-entity` y `tentacolous-operation` siempre se envian.
- **Capturas.** Reenvia una tabla a los sinks configurados sin escribir un metodo listener, con la
  anotacion `@TentacolousCapture` o un bean `Capture` equivalente. Ambas aceptan las mismas opciones
  que las anotaciones `@Upon...` — operaciones, `entityName`, `exclude`, filtro declarativo,
  `TentacolousFilter` programatico y `order` — y sus filtros deciden que cambios llegan a los sinks.

## Configuracion minima

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
  rabbitmq:
    host: localhost
    username: guest
    password: guest

tentacolous:
  kafka:
    enabled: true
    topic-prefix: "cdc."
  rabbitmq:
    enabled: true
    exchange: tentacolous
```

## Compatibilidad

- Las aplicaciones `0.2.x` existentes no se ven afectadas: los sinks son opcionales y las
  dependencias de los brokers tambien.
- Los seis dialectos (PostgreSQL, MySQL, MariaDB, SQL Server, Oracle, SQLite) siguen soportados
  igual que en `0.2.0`.

Consulta la [guia tecnica actual](../index.md) para la referencia completa de API y configuracion,
incluida la seccion [Colas de mensajes](../index.md#message-brokers).
