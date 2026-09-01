package io.github.aimtone.tentacolous.sink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aimtone.tentacolous.model.DbOperation;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EnvelopeChangeEventSerializerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EnvelopeChangeEventSerializer serializer = new EnvelopeChangeEventSerializer(objectMapper);

    @Test
    void buildsEnvelopeForInsert() throws Exception {
        ChangeEvent event = new ChangeEvent(7L, "Person", DbOperation.INSERT, "7",
                "{\"id\":7,\"name\":\"Ana\"}", null, Instant.parse("2026-09-01T12:00:00Z"));

        JsonNode envelope = objectMapper.readTree(serializer.serialize(event));

        assertThat(envelope.get("eventId").asLong()).isEqualTo(7L);
        assertThat(envelope.get("entity").asText()).isEqualTo("Person");
        assertThat(envelope.get("operation").asText()).isEqualTo("INSERT");
        assertThat(envelope.get("recordKey").asText()).isEqualTo("7");
        assertThat(envelope.get("observedAt").asText()).isEqualTo("2026-09-01T12:00:00Z");
        assertThat(envelope.get("before").isNull()).isTrue();
        assertThat(envelope.get("after").get("name").asText()).isEqualTo("Ana");
    }

    @Test
    void buildsEnvelopeForUpdateWithBeforeAndAfter() throws Exception {
        ChangeEvent event = new ChangeEvent(8L, "Person", DbOperation.UPDATE, "7",
                "{\"id\":7,\"name\":\"Ana Maria\"}", "{\"id\":7,\"name\":\"Ana\"}",
                Instant.parse("2026-09-01T12:00:00Z"));

        JsonNode envelope = objectMapper.readTree(serializer.serialize(event));

        assertThat(envelope.get("before").get("name").asText()).isEqualTo("Ana");
        assertThat(envelope.get("after").get("name").asText()).isEqualTo("Ana Maria");
    }

    @Test
    void buildsEnvelopeForDeleteWithBeforeOnly() throws Exception {
        ChangeEvent event = new ChangeEvent(9L, "Person", DbOperation.DELETE, "7",
                "{\"id\":7,\"name\":\"Ana\"}", null, Instant.parse("2026-09-01T12:00:00Z"));

        JsonNode envelope = objectMapper.readTree(serializer.serialize(event));

        assertThat(envelope.get("before").get("name").asText()).isEqualTo("Ana");
        assertThat(envelope.get("after").isNull()).isTrue();
    }
}
