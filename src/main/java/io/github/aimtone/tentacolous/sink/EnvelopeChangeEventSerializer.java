package io.github.aimtone.tentacolous.sink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.aimtone.tentacolous.model.DbOperation;

import java.nio.charset.StandardCharsets;

/** Serializes a {@link ChangeEvent} as the {@link MessageFormat#ENVELOPE} JSON document. */
public class EnvelopeChangeEventSerializer implements ChangeEventSerializer {

    private final ObjectMapper objectMapper;

    public EnvelopeChangeEventSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] serialize(ChangeEvent event) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", event.getEventId());
            envelope.put("entity", event.getEntityName());
            envelope.put("operation", event.getOperation().name());
            envelope.put("recordKey", event.getRecordKey());
            envelope.put("observedAt", event.getObservedAt().toString());
            envelope.set("before", readTree(beforeJson(event)));
            envelope.set("after", readTree(afterJson(event)));
            return objectMapper.writeValueAsBytes(envelope);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing change event " + event.getEventId(), e);
        }
    }

    private String beforeJson(ChangeEvent event) {
        if (event.getOperation() == DbOperation.UPDATE) {
            return event.getOldPayload();
        }

        if (event.getOperation() == DbOperation.DELETE) {
            return event.getPayload();
        }

        return null;
    }

    private String afterJson(ChangeEvent event) {
        return event.getOperation() == DbOperation.DELETE ? null : event.getPayload();
    }

    private JsonNode readTree(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return objectMapper.nullNode();
        }

        return objectMapper.readTree(json);
    }

    static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
