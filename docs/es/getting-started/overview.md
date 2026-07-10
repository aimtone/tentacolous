## Resumen {#overview}

Tentacolous es una libreria Spring Boot que ejecuta metodos Java cuando una tabla de base de datos recibe un `INSERT`, `UPDATE` o `DELETE`.

La diferencia importante es que Tentacolous reacciona a los cambios de la base de datos sin importar donde se originen.

**La version 0.1.8 se enfoca en filtros programaticos reutilizables.** Un filtro puede revisar la entidad actual, la entidad anterior durante un update y la operacion antes de decidir si el listener debe ejecutarse. Funciona con la nueva anotacion generica `@TentacolousListener` y con las anotaciones existentes `@UponInserting`, `@UponUpdating` y `@UponDeleting`.

**1.** Crea una tabla de eventos.

**2.** Crea una funcion PostgreSQL.

**3.** Crea triggers para las tablas que tienen listeners.

**4.** Los triggers escriben eventos en `db_change_event`.

**5.** Un poller Spring lee esos eventos.

**6.** Tentacolous convierte el payload JSON en tu entidad Java.

**7.** Tentacolous ejecuta el metodo anotado.
