package io.github.aimtone.tentacolous.sink;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Builds the {@link ChangeEventSerializer} that matches a configured {@link MessageFormat}. */
public final class ChangeEventSerializers {

    private ChangeEventSerializers() {
    }

    public static ChangeEventSerializer of(MessageFormat format, ObjectMapper objectMapper) {
        MessageFormat resolved = format == null ? MessageFormat.ENVELOPE : format;

        return switch (resolved) {
            case RAW -> new RawChangeEventSerializer();
            case ENVELOPE -> new EnvelopeChangeEventSerializer(
                    objectMapper == null ? new ObjectMapper() : objectMapper
            );
        };
    }
}
