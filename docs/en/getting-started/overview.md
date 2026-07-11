## Overview {#overview}

Tentacolous is a Spring Boot library that runs Java methods when a database table receives an `INSERT`, `UPDATE`, or `DELETE`.

The important difference is that Tentacolous reacts to database changes regardless of where they originate.

**Version 0.2.0 adds database-agnostic execution.** The same listener and filter API now works with PostgreSQL, MySQL, MariaDB, SQL Server, Oracle, and SQLite through automatically detected dialects.

**1.** It creates an event table.

**2.** It creates database-specific event infrastructure and triggers.

**3.** It creates triggers for tables that have listeners.

**4.** The triggers write events into `db_change_event`.

**5.** A Spring poller reads those events.

**6.** Tentacolous converts the JSON payload into your Java entity.

**7.** Tentacolous runs the annotated method.
