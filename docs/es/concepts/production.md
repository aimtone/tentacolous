## Produccion {#production}

- Crea la tabla, funcion y triggers usando migraciones controladas.
- Usa `tentacolous.schema-management=validate` para verificar la infraestructura.
- Si administras el schema manualmente, incluye las columnas `old_payload` y `record_key`, y pasa el campo de clave del registro a la funcion del trigger.
- Monitorea eventos `FAILED`.
- Limpia o archiva `db_change_event`.
- Evita logica lenta dentro de los listeners.
- Publica a una cola si el proceso es pesado.
