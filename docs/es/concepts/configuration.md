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

### Sinks para colas de mensajes

Estas propiedades solo se leen cuando la dependencia de Spring correspondiente esta en el classpath. Guia completa: [Colas de mensajes](message-brokers.md).

| Propiedad | Valor por defecto | Que hace |
| --- | --- | --- |
| `tentacolous.kafka.enabled` | `false` | Publica cada cambio en Kafka. |
| `tentacolous.kafka.topic` | *(vacio)* | Topic fijo. Si esta vacio, el topic es `topic-prefix` + nombre de entidad. |
| `tentacolous.kafka.topic-prefix` | *(vacio)* | Prefijo del topic por entidad, p. ej. `cdc.`. |
| `tentacolous.kafka.format` | `envelope` | `envelope` (antes/despues + metadata) o `raw` (payload de la fila). |
| `tentacolous.kafka.add-headers` | `true` | Agrega headers `tentacolous-*`. |
| `tentacolous.kafka.send-timeout` | `10s` | Espera del acuse del broker antes de fallar el evento. |
| `tentacolous.rabbitmq.enabled` | `false` | Publica cada cambio en RabbitMQ. |
| `tentacolous.rabbitmq.exchange` | `tentacolous` | Topic exchange donde publicar. |
| `tentacolous.rabbitmq.routing-key-prefix` | *(vacio)* | Prefijo de la routing key `entity.operation`. |
| `tentacolous.rabbitmq.format` | `envelope` | `envelope` o `raw`. |
| `tentacolous.rabbitmq.declare-exchange` | `true` | Declara el topic exchange durable al arrancar. |
| `tentacolous.rabbitmq.add-headers` | `true` | Agrega headers `tentacolous-*`. |

### Modos de schema management

| Modo | Uso comun | Comportamiento |
| --- | --- | --- |
| `auto` | Desarrollo | Crea la tabla, funcion y triggers si hace falta. |
| `create` | Ambientes de desarrollo controlados | Fuerza la creacion de infraestructura soportada. |
| `validate` | Produccion | Valida que la infraestructura exista sin crearla. |
| `none` | Produccion con migraciones | No crea ni valida infraestructura. |

Para comenzar, usa `schema-management: auto`. En produccion, `validate` o `none` suele ser mejor porque crear triggers requiere permisos elevados en la base de datos.
