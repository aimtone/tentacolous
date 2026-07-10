## Seguridad {#security}

- No guardes secretos en el payload.
- Usa `exclude` para columnas sensibles.
- Protege `db_change_event` con permisos adecuados de base de datos.
- En produccion, usa `schema-management=validate` o `schema-management=none`.
- Manten los listeners idempotentes porque pueden ocurrir reintentos.
