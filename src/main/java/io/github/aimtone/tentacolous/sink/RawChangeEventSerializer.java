package io.github.aimtone.tentacolous.sink;

import io.github.aimtone.tentacolous.model.DbOperation;

import java.nio.charset.StandardCharsets;

/** Serializes a {@link ChangeEvent} as its raw row payload ({@link MessageFormat#RAW}). */
public class RawChangeEventSerializer implements ChangeEventSerializer {

    @Override
    public byte[] serialize(ChangeEvent event) {
        String body = event.getPayload();

        if (body == null || body.isBlank()) {
            body = event.getOperation() == DbOperation.DELETE ? "null" : "{}";
        }

        return body.getBytes(StandardCharsets.UTF_8);
    }
}
