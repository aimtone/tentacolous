## Requirements {#requirements}

- Java 17 or higher.
- Spring Boot.
- A Spring Boot application with a configured `DataSource`.
- PostgreSQL, MySQL 8+, MariaDB, SQL Server 2016+, Oracle 19c+, or SQLite with JSON1, plus its JDBC driver.

All listed dialects have passed the same end-to-end listener matrix. For SQLite, use a record key that
is not reused during the event-history retention period, preferably `INTEGER PRIMARY KEY AUTOINCREMENT` or UUID.
