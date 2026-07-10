## Security {#security}

- Do not store secrets in the payload.
- Use `exclude` for sensitive columns.
- Protect `db_change_event` with proper database permissions.
- In production, use `schema-management=validate` or `schema-management=none`.
- Keep listeners idempotent because retries may happen.
