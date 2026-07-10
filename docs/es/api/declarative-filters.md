## Tipos de filtros {#filters}

Los filtros siempre usan esta estructura:

```text
field = "fieldName",
valueType = ValueType.TYPE,
value = "expected value"
```

`field`, `valueType` y `value` deben declararse juntos. Si falta uno, Tentacolous falla durante el escaneo de listeners y muestra un ejemplo de la forma valida.

| ValueType | Formato de value | Ejemplo |
| --- | --- | --- |
| `STRING` | Texto exacto | `"APPROVED"` |
| `BOOLEAN` | `true` or `false` | `"true"` |
| `NUMBER` | Numero entero largo | `"1"` |
| `INTEGER` | Numero entero | `"7"` |
| `LONG` | Numero entero largo | `"999"` |
| `DECIMAL` | Decimal exacto | `"10.50"` |
| `DOUBLE` | Decimal de punto flotante | `"3.14"` |
| `DATE` | Fecha ISO | `"2026-07-07"` |
| `TIME` | Hora ISO | `"13:45:00"` |
| `DATETIME` | Instant o datetime ISO | `"2026-07-07T13:45:00Z"` |
| `UUID` | UUID canonico | `"550e8400-e29b-41d4-a716-446655440000"` |
