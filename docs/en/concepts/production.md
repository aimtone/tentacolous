## Production {#production}

- Create the table, function and triggers using controlled migrations.
- Use `tentacolous.schema-management=validate` to verify infrastructure.
- If you manage schema manually, include the `old_payload` and `record_key` columns and pass the record key field to the trigger function.
- Monitor `FAILED` events.
- Clean up or archive `db_change_event`.
- Avoid slow logic inside listeners.
- Publish to a queue if the process is heavy.
