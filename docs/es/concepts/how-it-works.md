## Como funciona internamente {#internals}

Tentacolous no usa eventos JPA. Esto es intencional: los eventos JPA solo funcionan cuando el cambio pasa por la misma aplicacion. Tentacolous esta disenado para detectar cambios reales de base de datos, incluso cuando vienen desde fuera de tu aplicacion.

Para la base de datos detectada, Tentacolous crea:

- Una tabla `db_change_event`.
- Una funcion `db_change_event_notify_change()`.
- Un trigger por tabla y operacion.

El trigger almacena `entity_name`, `operation`, `payload`, `old_payload`, `record_key`, `status`, `attempts`, `last_error` y timestamps de procesamiento.

`payload` contiene la fila actual para inserts y updates, y la fila eliminada para deletes. `old_payload` contiene la fila anterior solo para updates. `record_key` identifica el registro afectado, usando la columna `@Id` de la entidad cuando existe o `id` en caso contrario.

Cuando un listener pide historico, Tentacolous consulta los payloads anteriores de `INSERT` y `UPDATE` para el mismo `entity_name` y `record_key`, ordenados desde el mas antiguo al mas reciente.

El poller busca eventos `PENDING`, los marca como `PROCESSING`, ejecuta el listener y finalmente los marca como `PROCESSED`. Si ocurre un error, Tentacolous guarda `last_error` y reintenta hasta alcanzar `tentacolous.max-attempts`.

### Sinks

Despues de ejecutar los metodos listener, el poller entrega el mismo evento a cada `ChangeEventSink` registrado (Kafka, RabbitMQ o el tuyo). Un fallo de un sink se trata como un fallo de listener: el evento queda `PENDING` y se reintenta, asi que los sinks reciben entrega *al menos una vez* y deben tolerar un `eventId` repetido. Como el trigger escribe el evento en la misma transaccion que el cambio de negocio, esto es el patron *transactional outbox*. Ver [Colas de mensajes](message-brokers.md).
