package io.github.aimtone.tentacolous.sink;

/** Turns a {@link ChangeEvent} into the bytes published to a broker. */
public interface ChangeEventSerializer {

    byte[] serialize(ChangeEvent event);

    default String contentType() {
        return "application/json";
    }
}
