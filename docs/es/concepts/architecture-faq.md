# Preguntas de arquitectura

Esta pagina responde las preguntas que suelen plantear los arquitectos antes de adoptar
Tentacolous. Es deliberadamente directa sobre los compromisos. Si falta una pregunta,
[abre un issue](https://github.com/aimtone/tentacolous/issues) y se agrega aqui.

## Posicionamiento

### Por que usar esto en lugar de Debezium?

Resuelven problemas que se solapan, pero con un costo operativo muy distinto.

| | Tentacolous | Debezium |
|---|---|---|
| Mecanismo de captura | Triggers SQL + una tabla de eventos que tu app consulta | Lee el log de transacciones (WAL / binlog / redo) |
| Infraestructura extra | Ninguna. Corre dentro de tu proceso Spring Boot actual | Kafka Connect (o Debezium Server / Embedded), ademas de Kafka en la topologia habitual |
| Privilegios de base de datos | Permiso para crear una tabla, una funcion y triggers | Privilegios de replicacion / lectura de log, a menudo un slot de replicacion o una feature de CDC habilitada por un DBA |
| Los cambios se consumen como | Metodos Java anotados en la misma app, y/o Kafka / RabbitMQ | Topicos de Kafka (o un sink connector) |
| Cambios de esquema / DDL | Se regeneran los triggers al re-ejecutar el schema management | Los maneja el topico de historial de esquema del connector |
| Captura `TRUNCATE`, operaciones masivas de metadatos | No (los triggers no se disparan con `TRUNCATE` en la mayoria de motores) | Si |
| Carga sobre la base primaria | El trigger escribe una fila extra por cambio capturado; el poller ejecuta `SELECT ... WHERE status = 'PENDING'` | Minima sobre la primaria; el lector de log va por fuera |
| Techo de throughput | Miles de eventos/segundo por instancia, limitado por la tabla de eventos | Muy alto; disenado para CDC de gran volumen |
| Consumidores multi-lenguaje | Solo a traves de un sink de broker | Nativo (topicos) |

**Elige Tentacolous cuando** quieres reacciones en proceso ante escrituras externas, no
puedes o no quieres operar Kafka Connect, no tienes privilegios de log en la base, y tu
volumen de cambios es moderado. Es una libreria, no una plataforma.

**Elige Debezium cuando** necesitas CDC de alto volumen, fidelidad a nivel de log
(incluyendo `TRUNCATE` y fronteras de transaccion), consumidores poliglotas, o ya operas
el ecosistema de Kafka Connect.

Tambien se combinan: hay equipos que usan Tentacolous para unas pocas tablas que
disparan logica de aplicacion y mantienen Debezium para el pipeline analitico.

### Esto es change-data-capture?

Es CDC basado en triggers con un [outbox transaccional](https://microservices.io/patterns/data/transactional-outbox.html).
El trigger escribe la fila de evento en la **misma transaccion** que el cambio de negocio,
asi el evento nunca se pierde si la transaccion hace commit y nunca es visible si hace
rollback. No es CDC basado en log, por lo que no ve cambios que el trigger no puede ver
(ver [limitaciones](#cuales-son-las-limitaciones-duras)).

### El polling no esta obsoleto? Por que no `LISTEN/NOTIFY` o un modelo push?

Consultar una unica tabla indexada cada 1 segundo es barato y portable entre los seis
motores soportados. Un modelo push (`LISTEN/NOTIFY`, `SqlTableDependency`, etc.) es
especifico del motor, igual no sobrevive a un reinicio del consumidor sin una consulta de
puesta al dia, y sigue necesitando la tabla de eventos durable para los reintentos. El
polling con tabla durable te da replay, reintentos y back-pressure gratis. Baja
`poll-interval` si necesitas menos latencia.

## Garantias

### Que garantias tengo?

- **Durabilidad / sin eventos perdidos.** El evento se escribe en la transaccion de
  negocio. Si el `COMMIT` de negocio tiene exito, el evento existe. Si hace rollback, el
  evento no existe. No hay ventana en la que la fila cambio pero el evento no.
- **Entrega al-menos-una-vez** a listeners y a sinks. Un evento se reintenta hasta que
  tiene exito o alcanza `max-attempts`. Un crash entre "el listener corrio" y "la fila
  quedo `PROCESSED`" produce una re-entrega al reiniciar.
- **Orden por registro.** Los eventos se procesan en orden de `id` (orden de insercion).
  Para una entidad + record key dada, los listeners ven los cambios en el orden en que se
  hicieron commit. El sink de Kafka usa el record key como clave del record, asi que una
  particion preserva ese orden aguas abajo.
- **No exactly-once.** Disena listeners y consumidores idempotentes. Usa `eventId` como
  clave de deduplicacion.
- **Despacho a-lo-sumo-una-vez por ciclo del poller.** Reclamar un evento es un
  `UPDATE ... WHERE id = ? AND status = 'PENDING'` atomico, asi que dos instancias o dos
  ciclos no pueden despachar el mismo evento.

### Los eventos se entregan en orden de transaccion? Y con escritores concurrentes?

Los eventos se ordenan por el `id` generado de la tabla de eventos. Con transacciones
concurrentes el `id` se asigna cuando se dispara el trigger, es decir al momento de la
escritura dentro de cada transaccion, y las filas solo se vuelven visibles para el poller
tras el commit. El poller lee las filas ya commiteadas en orden de `id`. El orden entre
registros distintos es best-effort; el orden **por registro** es confiable porque los
cambios de un mismo registro se serializan por los locks de fila de la tabla origen.

### El payload es un snapshot consistente de la fila?

Si, para la fila que cambio. `payload` es la imagen de fila que vio el trigger (la fila
nueva para `INSERT`/`UPDATE`, la fila eliminada para `DELETE`); `old_payload` es la
imagen previa para `UPDATE`. Es un snapshot de una sola fila, no una vista consistente
multi-tabla. Si necesitas datos relacionados, ensancha la entidad o cargalos en el
listener (asumiendo que ya reflejan un estado ligeramente posterior).

### En que contexto transaccional corre un listener?

Los listeners corren en el hilo y la transaccion del **poller**, *despues* de que la
transaccion de negocio ya hizo commit. No forman parte de la transaccion original y no
pueden vetarla. Si un listener escribe en la base y lanza una excepcion, sus propias
escrituras hacen rollback y el evento se reintenta; el cambio de negocio queda commiteado.

## Manejo de fallos

### Que ocurre si el listener falla?

1. La excepcion se captura y se registra con el id del evento, entidad, operacion y metodo.
2. Se guarda `last_error` en la fila del evento y se incrementa `attempts`.
3. Si `attempts < max-attempts`, la fila vuelve a `PENDING` y se reintenta en un ciclo
   posterior.
4. Cuando `attempts >= max-attempts`, la fila pasa a `FAILED` y se deja quieta.

Por los reintentos, **los listeners deben ser idempotentes**. Si varios listeners manejan
la misma entidad + operacion y uno lanza excepcion, el despacho de ese evento se detiene
(los listeners posteriores en `order` no corren) y el evento completo se reintenta, por lo
que los listeners anteriores tambien vuelven a correr.

### Que pasa si la aplicacion se cae a mitad del procesamiento?

- Crash **antes de reclamar**: el evento sigue `PENDING`, se toma en el siguiente arranque.
- Crash **despues de reclamar, antes de `PROCESSED`**: la fila queda en `PROCESSING`. El
  poller actual no recolecta automaticamente filas `PROCESSING` obsoletas, asi que en
  produccion debes monitorear filas atascadas en `PROCESSING` mas alla de un umbral (con
  `processing_started_at`) y devolverlas a `PENDING`. Es un compromiso deliberado a favor
  de la simplicidad; una consulta de recoleccion en un job programado son unas pocas
  lineas.
- Los datos de negocio nunca se ven afectados por esto: la fila del outbox es lo unico en
  vuelo.

### Que hago con los eventos `FAILED`?

Monitorea el conteo (`SELECT count(*) FROM db_change_event WHERE status = 'FAILED'`) y
alerta sobre el. La recuperacion es una decision manual o con script: corrige la causa
raiz y luego devuelve las filas a `PENDING` (y `attempts = 0`) para reprocesarlas, o
archivalas. Tentacolous no tiene una dead-letter queue; las filas `FAILED` *son* la tabla
de dead-letter.

### Que pasa si la base de datos esta caida?

El ciclo del poller lanza excepcion, se registra, y el siguiente ciclo reintenta. Nada se
pierde: los eventos no escritos nunca se commitearon (la transaccion de negocio tambien
fallo), y los eventos no procesados siguen `PENDING`. Cuando la base vuelve, el backlog se
drena en orden de `id`.

### Un listener lento bloquea todo?

Si. El procesamiento es de un solo hilo por instancia y secuencial dentro de un lote. Un
listener que tarda 5 segundos limita todo el pipeline a ~12 eventos/minuto en esa
instancia. Manten los cuerpos de listener rapidos; para trabajo pesado, delega a una cola
(un sink de broker, o tu propio executor) y retorna rapido. Esto se advierte en
[Produccion](production.md).

## Kafka y colas de mensajes

### Que pasa con Kafka? Lo necesito?

No. Kafka (y RabbitMQ) son **sinks opcionales**, desactivados por defecto. Los listeners
funcionan sin ningun broker. Habilita un sink cuando quieras que los cambios salgan del
proceso: para otros servicios, otros lenguajes, o un backbone de eventos.

### Como funciona el camino a Kafka?

El poller entrega cada evento a todos los `ChangeEventSink` registrados despues de correr
los listeners en proceso, dentro del mismo esquema de reintentos. El sink nativo de Kafka:

- publica un record por evento, **con clave = record key** (misma fila -> misma particion
  -> ordenado);
- usa `acks=all` y `enable.idempotence=true` por defecto (se sobreescribe con
  `spring.kafka.producer.*`);
- envia un cuerpo `envelope` (before/after + metadatos) o `raw`, mas headers
  `tentacolous-event-id` / `-entity` / `-operation`;
- falla el evento (-> reintento) si el broker no confirma dentro de `send-timeout`.

Como el sink corre despues de una transaccion commiteada y se reintenta ante fallo, la
entrega al broker es **al-menos-una-vez**; los consumidores deduplican por
`tentacolous-event-id`.

### Esto es un outbox transaccional hacia Kafka?

Si. La fila de evento se escribe en la transaccion de negocio; el relay a Kafka es un paso
aparte con reintento. Es el patron estandar outbox-a-broker, sin Kafka Connect. El costo
frente a Debezium es el throughput y el hecho de que el relay es tu proceso de aplicacion.

### Que orden / entrega ve el consumidor del broker?

Al-menos-una-vez, ordenado por registro dentro de una particion (los records llevan como
clave el record key). Sin orden global entre registros. Sin exactly-once hacia el
consumidor salvo que el consumidor lo implemente (escrituras idempotentes o una tabla de
ids procesados).

### Puedo usar Kafka y RabbitMQ a la vez, o mi propio transporte?

Si. Habilitar ambos registra ambos sinks y cada evento va a los dos (cada uno reintentado
como unidad, asi que ambos pueden ver una re-entrega). Para cualquier otra cosa,
implementa `ChangeEventSink` y exponlo como bean: un webhook, un topico SNS, un bus
interno. `order()` controla la posicion relativa a los sinks integrados.

## Incorporar tablas

### Puedo capturar tablas sin escribir codigo?

Si, con un **capture**. Crea el trigger y reenvia el cambio a los sinks sin un metodo
listener Java. Dos estilos equivalentes:

```java
@Configuration
@TentacolousCapture(entity = Order.class,
    actions = {ActionListener.INSERT, ActionListener.UPDATE},
    field = "status", valueType = ValueType.STRING, value = "APPROVED",
    exclude = {"internal_notes"})
public class CaptureConfig { }
```

```java
@Bean
Capture approvedOrders() {
    return Capture.of(Order.class)
        .operations(DbOperation.INSERT, DbOperation.UPDATE)
        .where("status", ValueType.STRING, "APPROVED")
        .exclude("internal_notes");
}
```

Los captures soportan el mismo selector de operaciones, `entityName`, `exclude`, filtro
declarativo, `TentacolousFilter` programatico y `order` que las anotaciones de listener.
El filtro del capture decide que cambios llegan a los sinks. Ver
[Colas de mensajes](message-brokers.md).

Un capture igual necesita una clase de entidad (o `entityName` + resolucion de tabla) para
que Tentacolous sepa el nombre de la tabla, la columna clave y el conjunto de columnas. No
escanea tablas arbitrarias que no hayas declarado.

### Necesito una entidad JPA?

Necesitas una clase que Tentacolous pueda mapear a un nombre de tabla y una clave. Una
`@Entity` JPA con `@Table` / `@Id` es el caso comun; el nombre de clase en snake_case y
una columna `id` son el fallback. `exclude` recorta columnas del payload.

### Y las tablas de otro equipo o un esquema legacy?

Ese es el caso de uso principal: Tentacolous reacciona a escrituras de cualquier origen.
Necesitas el privilegio de agregar un trigger a esa tabla, y deberias coordinar: un
trigger es visible para el dueno de la tabla y corre en sus escrituras. Usa
`schema-management: none` mas una migracion revisada para que el DDL pase por su proceso.

## Escalabilidad y operacion

### Como escala?

Verticalmente por instancia, y horizontalmente con matices.

**Por instancia** las palancas son:

| Palanca | Efecto |
|---|---|
| `poll-interval` | Menor = menos latencia, mas `SELECT`s vacios. `1s` por defecto. |
| `batch-size` | Eventos leidos por ciclo (defecto `100`). Subelo para rafagas con listeners rapidos. |
| Velocidad del listener | El techo real. Secuencial, un solo hilo por instancia. |
| `max-attempts` | Presupuesto de reintentos antes de `FAILED`. |

**Horizontalmente:** puedes correr varias instancias de la aplicacion contra la misma
base. Reclamar un evento es un `UPDATE` condicional atomico, asi que ningun evento se
procesa dos veces aunque todas las instancias hagan polling. Lo que *no* obtienes hoy es
eficiencia de work-stealing: cada instancia hace `SELECT` del mismo lote pendiente y luego
compite por reclamar filas, por lo que hay trabajo de lectura desperdiciado a medida que
crece el numero de instancias. Para unas pocas instancias esto esta bien. Si necesitas
muchos workers o throughput muy alto, reenvia a Kafka y escala consumidores alli.

**Techo practico:** el diseno apunta a volumenes moderados, aproximadamente hasta unos
pocos miles de eventos por segundo por instancia con listeners ligeros, limitado por la
tasa de insert y update de la tabla de eventos. Mas alla de eso, usa CDC basado en log.

### Cual es la carga sobre la base de datos origen?

- **Escrituras:** cada `INSERT`/`UPDATE`/`DELETE` capturado en una tabla observada hace un
  `INSERT` extra en la tabla de eventos dentro de la misma transaccion. Aproximadamente
  duplica el costo de escritura de una sentencia capturada y alarga un poco la
  transaccion.
- **Lecturas:** un `SELECT ... WHERE status = 'PENDING' ORDER BY id LIMIT n` indexado por
  intervalo de polling por instancia, mas un `UPDATE` por evento para reclamarlo y otro
  para finalizarlo.
- **Crecimiento:** la tabla de eventos crece con el volumen de cambios. Debes archivar o
  borrar las filas procesadas (un `DELETE FROM db_change_event WHERE status = 'PROCESSED'
  AND processed_at < now() - interval '7 days'` programado, o drop de particion). Los
  listeners con historial leen filas pasadas del mismo registro, asi que conserva
  suficiente retencion para cubrir el historial mas profundo que uses.

### Cuanta latencia hay desde el cambio hasta el listener?

Aproximadamente `poll-interval` en promedio (la mitad mas el tiempo de procesamiento), o
sea ~0.5-1 s con los valores por defecto. Baja `poll-interval` a `100ms`-`250ms` para
casi tiempo real a costa de mas consultas ociosas. No es sub-milisegundo; si necesitas
eso, esta no es la herramienta.

### Puedo correrlo con alta disponibilidad?

Si. Corre N instancias; si una muere las demas siguen haciendo polling y reclamando. No
hay eleccion de lider ni split brain porque las reclamaciones son atomicas. Durante un
despliegue rolling, un evento en `PROCESSING` en la instancia que se apaga necesita el
manejo de filas obsoletas descrito en
[manejo de fallos](#que-pasa-si-la-aplicacion-se-cae-a-mitad-del-procesamiento).

### Como lo observo?

- La tabla `db_change_event` es la fuente de verdad: consulta conteos por `status`, el
  `PENDING` mas antiguo, filas en `PROCESSING` pasado un umbral, `FAILED` con `last_error`.
- El poller registra en `INFO` al arrancar y en `ERROR` en fallos de ciclo y por evento.
- Alertas recomendadas: `FAILED > 0`, antiguedad del `PENDING` mas viejo, antiguedad de
  `PROCESSING`, cantidad / tamano de filas de la tabla de eventos.
- Todavia no hay metricas Micrometer integradas; envuelve un `ChangeEventSink` o una
  consulta programada si quieres gauges.

### Como lo revierto o lo apago?

Pon `tentacolous.enabled: false` y el poller no arranca. Los triggers siguen escribiendo
en la tabla de eventos (inofensivo, solo crecimiento) hasta que los elimines. Para
quitarlo del todo: para la app, dropea los triggers y la funcion, dropea la tabla de
eventos. Como los listeners son beans Spring normales y la libreria es un unico JAR con
dependencias de broker opcionales, no hay plataforma que desmantelar.

### Genera lock-in?

Bajo lock-in. Las anotaciones son la unica superficie especifica de Tentacolous en tu
codigo, y envuelven metodos ordinarios. La tabla de eventos es una tabla SQL normal que
puedes leer con cualquier cosa. Si lo superas, el formato de mensaje `envelope` se parece
a la forma de Debezium, asi que migrar a CDC basado en log es sobre todo re-apuntar
consumidores.

## Seguridad

### Cuales son las consideraciones de seguridad?

- La tabla de eventos guarda los payloads de fila como JSON. Tratala como si contuviera la
  misma clasificacion de datos que las tablas origen. Usa `exclude` para descartar
  secretos, tokens y PII que no necesitas aguas abajo.
- Restringe `SELECT`/`UPDATE` sobre `db_change_event` al rol de la aplicacion.
- Crear triggers requiere privilegios elevados; en produccion prefiere
  `schema-management: validate` o `none` y aplica el DDL con una migracion revisada para
  que el rol de runtime de la app no tenga permisos de DDL.
- Los sinks de broker envian payloads fuera de la maquina: aplica seguridad de transporte
  (TLS) y ACLs de topico como con cualquier stream de eventos.

Ver [Seguridad](security.md).

## Limitaciones

### Cuales son las limitaciones duras?

- **Solo cambios visibles para el trigger.** `TRUNCATE` no dispara triggers de fila en la
  mayoria de motores; algunos caminos de carga masiva y el apply de replicacion pueden
  saltarse los triggers. Operaciones de particion y DDL no se capturan.
- **No exactly-once.** Al-menos-una-vez en todas partes; la idempotencia es tu
  responsabilidad.
- **Procesamiento de un solo hilo por instancia.** El throughput lo limitan la velocidad
  del listener y la tabla de eventos.
- **Sin recoleccion automatica de filas `PROCESSING` obsoletas** — agregas el
  monitoreo/reset.
- **Sin metricas integradas ni UI de administracion.** La tabla es la interfaz.
- **El mantenimiento de la tabla de eventos es tuyo** — hay que configurar
  archivado / particionado.
- **Acoplamiento de esquema.** Un trigger referencia las columnas de la tabla observada;
  un DDL incompatible requiere regenerar el trigger (re-ejecutar schema management o
  migrar).
- **SQLite:** claves primarias reutilizadas pueden fusionar historiales no relacionados —
  usa `AUTOINCREMENT` o UUIDs para las tablas observadas.

Si esto es inaceptable para tus necesidades de volumen o fidelidad, usa CDC basado en log.
