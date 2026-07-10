## Overview {#overview}

Tentacolous is a Spring Boot library that runs Java methods when a database table receives an `INSERT`, `UPDATE`, or `DELETE`.

The important difference is that Tentacolous reacts to database changes regardless of where they originate.

**Version 0.1.8 focuses on reusable programmatic filters.** A filter can inspect the current entity, the previous entity during updates, and the operation before deciding whether the listener should run. This works with the new generic `@TentacolousListener` and with the existing `@UponInserting`, `@UponUpdating`, and `@UponDeleting` annotations.

**1.** It creates an event table.

**2.** It creates a PostgreSQL function.

**3.** It creates triggers for tables that have listeners.

**4.** The triggers write events into `db_change_event`.

**5.** A Spring poller reads those events.

**6.** Tentacolous converts the JSON payload into your Java entity.

**7.** Tentacolous runs the annotated method.
