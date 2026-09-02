# Architecture FAQ

This page answers the questions architects usually raise before adopting Tentacolous.
It is deliberately blunt about the trade-offs. If a question is missing,
[open an issue](https://github.com/aimtone/tentacolous/issues) and it will be added here.

## Positioning

### Why use this instead of Debezium?

They solve overlapping problems with very different operational cost.

| | Tentacolous | Debezium |
|---|---|---|
| Capture mechanism | SQL triggers + an event table polled by your app | Reads the transaction log (WAL / binlog / redo) |
| Extra infrastructure | None. It runs inside your existing Spring Boot process | Kafka Connect (or Debezium Server / Embedded), plus Kafka in the usual topology |
| Database privileges | Rights to create a table, a function and triggers | Replication / log-reading privileges, often a dedicated replication slot or CDC feature enabled by a DBA |
| Consumes changes as | Annotated Java methods in the same app, and/or Kafka / RabbitMQ | Kafka topics (or a sink connector) |
| Schema/DDL changes | Handled by re-running schema management; triggers are regenerated | Handled by the connector's schema history topic |
| Captures `TRUNCATE`, bulk metadata ops | No (triggers do not fire for `TRUNCATE` on most engines) | Yes |
| Load on the primary DB | Trigger writes one extra row per captured change; poller runs `SELECT ... WHERE status = 'PENDING'` | Minimal on the primary; the log reader is out of band |
| Throughput ceiling | Thousands of events/second per instance, bounded by the event table | Very high; designed for firehose CDC |
| Multi-language consumers | Only through a broker sink | Native (topics) |

**Choose Tentacolous when** you want in-process reactions to external writes, you cannot
or do not want to run Kafka Connect, you do not have log-level DB privileges, and your
change volume is moderate. It is a library, not a platform.

**Choose Debezium when** you need high-volume CDC, log-level fidelity (including
`TRUNCATE` and transaction boundaries), polyglot consumers, or you already run the Kafka
Connect ecosystem.

They also compose: some teams use Tentacolous for a handful of tables that drive
application logic and keep Debezium for the analytics pipeline.

### Is this change-data-capture?

It is trigger-based CDC with a [transactional outbox](https://microservices.io/patterns/data/transactional-outbox.html).
The trigger writes the event row in the **same transaction** as the business change, so
the event is never lost if the transaction commits and never visible if it rolls back.
It is not log-based CDC, so it does not see changes the trigger cannot see (see
[limitations](#what-are-the-hard-limitations)).

### Isn't polling old-fashioned? Why not `LISTEN/NOTIFY` or a push model?

Polling a single indexed table on a 1-second interval is cheap and portable across all six
supported engines. A push model (`LISTEN/NOTIFY`, `SqlTableDependency`, etc.) is
engine-specific, does not survive a consumer restart without a catch-up query anyway, and
still needs the durable event table for retries. Polling with a durable table gives you
replay, retry and back-pressure for free. Tune `poll-interval` down if you need lower
latency.

## Guarantees

### What guarantees do I get?

- **Durability / no lost events.** The event is written in the business transaction. If the
  business `COMMIT` succeeds, the event exists. If it rolls back, the event is gone. There
  is no window where the row changed but the event did not.
- **At-least-once delivery** to listeners and to sinks. An event is retried until it
  succeeds or reaches `max-attempts`. A crash between "listener ran" and "row marked
  `PROCESSED`" causes a redelivery on restart.
- **Per-record ordering.** Events are processed in `id` order (insertion order). For a
  given entity + record key, listeners see changes in the order they were committed. The
  Kafka sink keys records by record key, so a partition preserves that order downstream.
- **No exactly-once.** Design listeners and consumers to be idempotent. Use `eventId` as
  the deduplication key.
- **At-most-once *dispatch* per poller cycle.** Claiming an event is an atomic
  `UPDATE ... WHERE id = ? AND status = 'PENDING'`, so two instances or two cycles cannot
  both dispatch the same event.

### Are events delivered in transaction order? What about concurrent writers?

Events are ordered by the event table's generated `id`. Under concurrent transactions the
`id` is assigned when the trigger fires, i.e. at write time within each transaction, and
rows only become visible to the poller after commit. The poller reads committed rows in
`id` order. Cross-record ordering is best-effort; **per-record** ordering is reliable
because a single record's changes are serialized by row locks in the source table.

### Is the payload a consistent snapshot of the row?

Yes for the row that changed. `payload` is the row image the trigger saw (the new row for
`INSERT`/`UPDATE`, the removed row for `DELETE`); `old_payload` is the pre-image for
`UPDATE`. It is a single-row snapshot, not a multi-table consistent view. If you need
related data, either widen the entity or load it in the listener (accepting that it now
reflects a slightly later state).

### What isolation / transactional context does a listener run in?

Listeners run in the **poller's** thread and transaction, *after* the business transaction
already committed. They are not part of the original transaction and cannot veto it. If a
listener writes to the database and throws, its own writes roll back and the event is
retried; the business change stays committed.

## Failure handling

### What happens if the listener fails?

1. The exception is caught and logged with the event id, entity, operation and method.
2. `last_error` is stored on the event row and `attempts` is incremented.
3. If `attempts < max-attempts`, the row goes back to `PENDING` and is retried on a later
   poll cycle.
4. When `attempts >= max-attempts`, the row is set to `FAILED` and left alone.

Because of retries, **listeners must be idempotent**. If several listeners handle the same
entity + operation and one throws, dispatch for that event stops (later listeners in
`order` do not run) and the whole event is retried — so earlier listeners run again too.

### What happens if the application crashes mid-processing?

- Crash **before claim**: the event is still `PENDING`, picked up on the next start.
- Crash **after claim, before `PROCESSED`**: the row is left in `PROCESSING`. The current
  poller does not automatically reap stale `PROCESSING` rows, so in production you should
  monitor for rows stuck in `PROCESSING` beyond a threshold (using `processing_started_at`)
  and reset them to `PENDING`. This is a deliberate simplicity trade-off; a supervised
  reaper query in a scheduled job is a few lines.
- The business data is never affected by any of this — the outbox row is the only thing in
  flight.

### What do I do with `FAILED` events?

Monitor the count (`SELECT count(*) FROM db_change_event WHERE status = 'FAILED'`) and
alert on it. Recovery is a manual or scripted decision: fix the root cause, then set the
rows back to `PENDING` (and `attempts = 0`) to replay them, or archive them. Tentacolous
does not have a dead-letter queue; the `FAILED` rows *are* the dead-letter table.

### What if the database is down?

The poller cycle throws, is logged, and the next cycle retries. Nothing is lost: unwritten
events were never committed (the business transaction also failed), and unprocessed events
are still `PENDING`. When the database returns the backlog drains in `id` order.

### Does a slow listener block everything?

Yes. Processing is single-threaded per instance and sequential within a batch. A listener
that takes 5 seconds throttles the whole pipeline to ~12 events/minute on that instance.
Keep listener bodies fast; for heavy work, hand off to a queue (a broker sink, or your own
executor) and return quickly. This is called out in [Production](production.md).

## Kafka and message brokers

### What about Kafka? Do I need it?

No. Kafka (and RabbitMQ) are **optional sinks**, off by default. Listeners work with no
broker at all. Enable a sink when you want changes to leave the process — for other
services, other languages, or an event backbone.

### How does the Kafka path work?

The poller hands each event to every registered `ChangeEventSink` after the in-process
listeners run, in the same retry envelope. The native Kafka sink:

- publishes one record per event, **keyed by record key** (same row → same partition →
  ordered);
- uses `acks=all` and `enable.idempotence=true` by default (override via
  `spring.kafka.producer.*`);
- sends an `envelope` (before/after + metadata) or `raw` body, plus `tentacolous-event-id`
  / `-entity` / `-operation` headers;
- fails the event (→ retry) if the broker does not ack within `send-timeout`.

Because the sink runs after a committed transaction and is retried on failure, broker
delivery is **at-least-once**; consumers dedupe on `tentacolous-event-id`.

### Is this a transactional outbox to Kafka?

Yes. The event row is written in the business transaction; the relay to Kafka is a
separate step with retry. This is the standard outbox-to-broker pattern, without Kafka
Connect. The cost versus Debezium is throughput and the fact that the relay is your app
process.

### What ordering / delivery does the broker consumer see?

At-least-once, per-record ordered within a partition (records are keyed by record key). No
global ordering across records. No exactly-once into the consumer unless the consumer
implements it (idempotent writes or a processed-id table).

### Can I use both Kafka and RabbitMQ, or my own transport?

Yes. Enabling both registers both sinks and every event goes to both (each retried as a
unit, so both can see a redelivery). For anything else, implement `ChangeEventSink` and
expose it as a bean — a webhook, an SNS topic, an internal bus. `order()` controls
placement relative to the built-in sinks.

## Onboarding tables

### Can I capture tables without writing code?

Yes, with a **capture**. It creates the trigger and forwards the change to the sinks with
no Java listener method. Two equivalent styles:

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

Captures support the same operations selector, `entityName`, `exclude`, declarative filter,
programmatic `TentacolousFilter`, and `order` as the listener annotations. The capture's
filter decides which changes reach the sinks. See [Message brokers](message-brokers.md).

A capture still needs an entity class (or `entityName` + table resolution) so Tentacolous
knows the table name, key column and column set. It does not scan arbitrary tables you
have not declared.

### Do I need a JPA entity?

You need a class Tentacolous can map to a table name and key. A JPA `@Entity` with
`@Table` / `@Id` is the common case; the snake-cased class name and an `id` column are the
fallback. `exclude` trims columns from the payload.

### What about tables owned by another team or a legacy schema?

That is the main use case — Tentacolous reacts to writes from anywhere. You do need the
privilege to add a trigger to that table, and you should coordinate: a trigger is visible
to the table owner and runs on their writes. Use `schema-management: none` plus a
reviewed migration so the DDL goes through their process.

## Scaling and operations

### How does it scale?

Vertically per instance, and horizontally with caveats.

**Per instance** the levers are:

| Lever | Effect |
|---|---|
| `poll-interval` | Lower = less latency, more empty `SELECT`s. `1s` is the default. |
| `batch-size` | Events pulled per cycle (default `100`). Raise for bursty, fast listeners. |
| Listener speed | The real ceiling. Sequential, single-threaded per instance. |
| `max-attempts` | Retry budget before `FAILED`. |

**Horizontally:** you can run multiple application instances against the same database.
Event claiming is an atomic conditional `UPDATE`, so no event is processed twice even if
every instance polls. What you do *not* get today is work-stealing efficiency — each
instance `SELECT`s the same pending batch and then races to claim rows, so there is wasted
read work as instance count grows. For a handful of instances this is fine. If you need
many workers or very high throughput, forward to Kafka and scale consumers there instead.

**Practical ceiling:** the design targets moderate volumes — roughly up to low thousands
of events per second through one instance with lightweight listeners, bounded by the event
table's insert and update rate. Beyond that, use log-based CDC.

### What is the load on the source database?

- **Writes:** each captured `INSERT`/`UPDATE`/`DELETE` on a watched table does one extra
  `INSERT` into the event table inside the same transaction. Roughly doubles the write
  cost of a captured statement and slightly lengthens the transaction.
- **Reads:** one indexed `SELECT ... WHERE status = 'PENDING' ORDER BY id LIMIT n` per
  poll interval per instance, plus one `UPDATE` per event to claim and one to finish.
- **Growth:** the event table grows with change volume. You must archive or delete
  processed rows (a scheduled `DELETE FROM db_change_event WHERE status = 'PROCESSED' AND
  processed_at < now() - interval '7 days'` or a partition drop). History-based listeners
  read past rows for the same record, so keep enough retention to cover the deepest
  history you use.

### How much latency does a change take to reach a listener?

Roughly `poll-interval` on average (half of it plus processing time), so ~0.5–1 s with
defaults. Drop `poll-interval` to `100ms`–`250ms` for near-real-time at the cost of more
idle queries. It is not sub-millisecond; if you need that, this is the wrong tool.

### Can I run it with high availability?

Yes. Run N instances; if one dies the others keep polling and claiming. There is no leader
election and no split brain because claims are atomic. During a rolling deploy an event
in `PROCESSING` on the instance being killed needs the stale-row handling described in
[failure handling](#what-happens-if-the-application-crashes-mid-processing).

### How do I observe it?

- The `db_change_event` table is the source of truth: query counts by `status`, oldest
  `PENDING`, rows in `PROCESSING` past a threshold, `FAILED` with `last_error`.
- The poller logs at `INFO` on start and `ERROR` on cycle and per-event failures.
- Recommended alerts: `FAILED > 0`, oldest `PENDING` age, `PROCESSING` age, event table
  row count / size.
- There are no built-in Micrometer metrics yet; wrap a `ChangeEventSink` or a scheduled
  query if you want gauges.

### How do I roll it back or turn it off?

Set `tentacolous.enabled: false` — the poller does not start. The triggers still write to
the event table (harmless, just growth) until you drop them. To fully remove: stop the
app, drop the triggers and the function, drop the event table. Because listeners are
plain Spring beans and the library is a single JAR with optional broker deps, there is no
platform to decommission.

### Does it lock me in?

Low lock-in. The annotations are the only Tentacolous-specific surface in your code, and
they wrap ordinary methods. The event table is a plain SQL table you can read with
anything. If you outgrow it, the `envelope` message format is close to Debezium's shape,
so a migration to log-based CDC is mostly re-pointing consumers.

## Security

### What are the security considerations?

- The event table stores row payloads as JSON. Treat it as containing the same data
  classification as the source tables. Use `exclude` to drop secrets, tokens and PII you
  do not need downstream.
- Restrict `SELECT`/`UPDATE` on `db_change_event` to the application role.
- Creating triggers needs elevated privileges; in production prefer
  `schema-management: validate` or `none` and apply the DDL through a reviewed migration so
  the app's runtime role does not hold DDL rights.
- Broker sinks send payloads off the box — apply transport security (TLS) and topic ACLs
  as you would for any event stream.

See [Security](security.md).

## Limitations

### What are the hard limitations?

- **Trigger-visible changes only.** `TRUNCATE` does not fire row triggers on most engines;
  some bulk-load paths and replication apply may bypass triggers. Partition operations and
  DDL are not captured.
- **No exactly-once.** At-least-once everywhere; idempotency is your responsibility.
- **Single-threaded processing per instance.** Throughput is bounded by listener speed and
  the event table.
- **No automatic reaping of stale `PROCESSING` rows** — you add the monitor/reset.
- **No built-in metrics or admin UI.** The table is the interface.
- **Event table maintenance is yours** — archival / partitioning must be set up.
- **Schema coupling.** A trigger references the watched table's columns; incompatible DDL
  requires regenerating the trigger (re-run schema management or migrate).
- **SQLite:** reused primary keys can merge unrelated histories — use
  `AUTOINCREMENT` or UUIDs for watched tables.

If these are dealbreakers for your volume or fidelity needs, use log-based CDC.
