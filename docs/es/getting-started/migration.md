## Migracion desde 0.1.7 {#migration}

La version `0.1.8` mantiene compatibilidad con los listeners existentes. No necesitas reemplazar `@UponInserting`, `@UponUpdating` ni `@UponDeleting`, y esta version no requiere cambios en la infraestructura de base de datos.

- Usa `@TentacolousListener` solo si prefieres la anotacion generica con `action`.
- Los filtros personalizados son beans opcionales de Spring que extienden `TentacolousFilter<T>`.
- El tipo generico del filtro debe ser compatible con la entidad del listener.
- Si un filtro personalizado se declara junto con `field`, `valueType` o `value`, el filtro personalizado tiene prioridad y Tentacolous escribe un warning.
- Los filtros declarativos deben definir `field`, `valueType` y `value` juntos.
- Cada metodo puede declarar una anotacion listener por operacion.
