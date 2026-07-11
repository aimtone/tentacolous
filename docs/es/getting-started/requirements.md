## Requisitos {#requirements}

- Java 17 o superior.
- Spring Boot.
- Una aplicacion Spring Boot con un `DataSource` configurado.
- PostgreSQL, MySQL 8+, MariaDB, SQL Server 2016+, Oracle 19c+ o SQLite con JSON1, junto con su driver JDBC.

Todos los dialectos indicados superaron la misma matriz de listeners de extremo a extremo. En SQLite,
usa una clave de registro que no se reutilice durante la retencion del historial, preferiblemente
`INTEGER PRIMARY KEY AUTOINCREMENT` o UUID.
