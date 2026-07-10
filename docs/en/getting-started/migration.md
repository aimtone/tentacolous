## Migrating from 0.1.7 {#migration}

Version `0.1.8` remains compatible with existing listeners. You do not need to replace `@UponInserting`, `@UponUpdating`, or `@UponDeleting`, and this release requires no database infrastructure changes.

- Use `@TentacolousListener` only when you prefer the generic annotation with `action`.
- Custom filters are optional Spring beans extending `TentacolousFilter<T>`.
- The filter generic type must be compatible with the listener entity.
- If a custom filter is declared together with `field`, `valueType`, or `value`, the custom filter has priority and Tentacolous logs a warning.
- Declarative filters must define `field`, `valueType`, and `value` together.
- Each method may declare one listener annotation per operation.
