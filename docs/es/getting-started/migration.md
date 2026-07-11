## Migracion a 0.2.0 {#migration}

La version `0.2.0` conserva la API de listeners de `0.1.8` y añade dialectos de base de datos. Las aplicaciones PostgreSQL existentes no necesitan cambiar anotaciones ni properties de Tentacolous.

1. Actualiza la dependencia a `0.2.0`.
2. Conserva solamente el driver JDBC de la base utilizada por la aplicacion.
3. Verifica que el usuario tenga permisos para crear tablas y triggers cuando `schema-management` sea `auto` o `create`.
4. Ejecuta la aplicacion en un ambiente no productivo y valida listeners INSERT, UPDATE y DELETE.

Las aplicaciones con `schema-management=none` siguen siendo responsables de instalar manualmente la infraestructura especifica para su motor.

En SQLite, usa una clave que no se reutilice durante la retencion del historial. Prefiere UUID o `INTEGER PRIMARY KEY AUTOINCREMENT` en las tablas de negocio.
