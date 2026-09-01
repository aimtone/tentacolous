package io.github.aimtone.tentacolous.sink;

/**
 * Destination for database change events detected by Tentacolous.
 *
 * <p>Every sink registered as a Spring bean is invoked by the poller for each event, after the
 * in-process listener methods have run. If {@link #publish(ChangeEvent)} throws, the event is not
 * marked as processed and follows the normal retry flow, so a sink must tolerate redelivery of the
 * same {@link ChangeEvent#getEventId() event id} (at-least-once delivery).
 */
public interface ChangeEventSink {

    /** Short name used in logs and metrics, for example {@code "kafka"}. */
    String name();

    /**
     * Forwards the change to the underlying transport. Should block until the write is acknowledged
     * so that a failure can trigger a retry.
     */
    void publish(ChangeEvent event) throws Exception;

    /** Allows a sink to skip events it is not interested in. Defaults to accepting everything. */
    default boolean supports(ChangeEvent event) {
        return true;
    }

    /** Lower values run first when several sinks are registered. Defaults to {@code 0}. */
    default int order() {
        return 0;
    }
}
