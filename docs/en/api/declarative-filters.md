## Filter types {#filters}

Filters always use this structure:

```text
field = "fieldName",
valueType = ValueType.TYPE,
value = "expected value"
```

`field`, `valueType`, and `value` must be declared together. If one is missing, Tentacolous fails during listener scanning and prints an example of the valid form.

| ValueType | value format | Example |
| --- | --- | --- |
| `STRING` | Exact text | `"APPROVED"` |
| `BOOLEAN` | `true` or `false` | `"true"` |
| `NUMBER` | Long integer number | `"1"` |
| `INTEGER` | Integer number | `"7"` |
| `LONG` | Long integer number | `"999"` |
| `DECIMAL` | Exact decimal | `"10.50"` |
| `DOUBLE` | Floating point decimal | `"3.14"` |
| `DATE` | ISO date | `"2026-07-07"` |
| `TIME` | ISO time | `"13:45:00"` |
| `DATETIME` | ISO instant or datetime | `"2026-07-07T13:45:00Z"` |
| `UUID` | Canonical UUID | `"550e8400-e29b-41d4-a716-446655440000"` |
