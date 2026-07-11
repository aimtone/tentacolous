## Resumen {#overview}

Tentacolous es una libreria Spring Boot que ejecuta metodos Java cuando una tabla de base de datos recibe un `INSERT`, `UPDATE` o `DELETE`.

La diferencia importante es que Tentacolous reacciona a los cambios de la base de datos sin importar donde se originen.

**La version 0.2.0 añade ejecucion agnostica a la base de datos.** La misma API de listeners y filtros funciona con PostgreSQL, MySQL, MariaDB, SQL Server, Oracle y SQLite mediante dialectos detectados automaticamente.

**1.** Crea una tabla de eventos.

**2.** Crea infraestructura de eventos y triggers especificos para el motor.

**3.** Crea triggers para las tablas que tienen listeners.

**4.** Los triggers escriben eventos en `db_change_event`.

**5.** Un poller Spring lee esos eventos.

**6.** Tentacolous convierte el payload JSON en tu entidad Java.

**7.** Tentacolous ejecuta el metodo anotado.
