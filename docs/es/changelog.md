# Historial de cambios

Todos los cambios importantes de Tentacolous se documentan en esta página.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/).

## 0.1.8 — 2026-07-10

### Añadido

- Se añadieron filtros programáticos administrados por Spring para `@UponInserting`, `@UponUpdating`, `@UponDeleting` y `@TentacolousListener` mediante el nuevo parámetro de anotación `filter`.
- Se añadieron `TentacolousFilter<T>` y `TentacolousFilterContext<T>`, con acceso a la entidad actual, la entidad anterior durante actualizaciones y la operación de base de datos.
- Se añadió la anotación genérica `@TentacolousListener` con `ActionListener.INSERT`, `ActionListener.UPDATE` y `ActionListener.DELETE` como alternativa a las anotaciones específicas por operación.
- Se añadió documentación versionada en inglés y español para `0.1.8`, incluidos ejemplos de listeners genéricos y filtros personalizados.

### Comportamiento

- Un filtro personalizado tiene prioridad sobre los filtros declarativos `field`, `valueType` y `value` cuando se configuran ambos estilos; Tentacolous registra una advertencia.
- Los filtros declarativos ahora deben definir conjuntamente `field`, `valueType` y `value`.
- Los listeners existentes con `@UponInserting`, `@UponUpdating` y `@UponDeleting` siguen siendo totalmente compatibles.
- Los tipos de entidad de los filtros personalizados se validan al registrar los listeners.
- Los métodos pueden declarar varias anotaciones listener de Tentacolous sólo cuando cada anotación apunta a una operación diferente.
- Los contextos de filtro ahora exponen el ID del evento, el nombre de la entidad y la clave del registro.
- Los listeners filtrados reutilizan las entidades deserializadas al ejecutarse.
- Los errores de filtros y listeners ahora incluyen detalles de la operación, entidad, método y evento.
- Todas las anotaciones listener admiten orden de ejecución ascendente mediante el parámetro `order`.
- Los contextos de filtro ahora exponen `getChangedFields()` y `hasChanged(...)` para eventos de actualización.

## 0.1.7 — 2026-07-09

### Añadido

- Se añadió soporte opcional para la entidad anterior en listeners `@UponUpdating`.
- Se añadió soporte opcional para el historial de cambios en listeners `@UponUpdating` y `@UponDeleting`.
- Se añadió soporte para parámetros de historial como `List<Entity>` o `Entity[]`.
- Se añadieron los metadatos de evento `old_payload` y `record_key` para soportar valores anteriores e historial de registros.
- Se añadió migración automática de esquema para las nuevas columnas de eventos cuando la administración de esquema está habilitada.

### Modificado

- `@UponUpdating` ahora admite uno, dos o tres parámetros:
    - entidad actual;
    - entidad actual y entidad anterior;
    - entidad actual, entidad anterior e historial.
- `@UponDeleting` ahora admite uno o dos parámetros:
    - entidad eliminada;
    - entidad eliminada e historial.
- Se actualizaron el README y los ejemplos de documentación para la versión `0.1.7`.

### Notas

- Los proyectos que usan `tentacolous.schema-management=none` deben actualizar manualmente su infraestructura de base de datos para incluir las nuevas columnas `old_payload` y `record_key`, además de la función trigger actualizada.

## 0.1.6 — 2026-07-07

### Añadido

- Primera versión pública de Tentacolous.
- Funcionalidad principal de la librería.
- API inicial.
- Documentación básica y ejemplos de uso.

### Corregido

- Todavía no hay correcciones.

### Problemas conocidos

- Esta es una versión inicial.
- Algunas funcionalidades pueden cambiar en versiones futuras.
- Todavía pueden existir errores.
