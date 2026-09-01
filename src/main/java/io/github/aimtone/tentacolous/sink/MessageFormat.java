package io.github.aimtone.tentacolous.sink;

/** Wire format used by a sink when publishing a {@link ChangeEvent}. */
public enum MessageFormat {

    /**
     * A self-describing JSON envelope:
     * <pre>
     * {
     *   "eventId": 42,
     *   "entity": "Person",
     *   "operation": "UPDATE",
     *   "recordKey": "7",
     *   "observedAt": "2026-09-01T12:00:00Z",
     *   "before": { ... },
     *   "after": { ... }
     * }
     * </pre>
     */
    ENVELOPE,

    /**
     * The raw row payload exactly as the database trigger produced it (the current row for
     * {@code INSERT}/{@code UPDATE}, the removed row for {@code DELETE}). No metadata is added to
     * the body; it is still available in message headers.
     */
    RAW
}
