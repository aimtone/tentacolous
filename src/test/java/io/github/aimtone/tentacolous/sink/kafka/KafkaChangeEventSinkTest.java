package io.github.aimtone.tentacolous.sink.kafka;

import io.github.aimtone.tentacolous.model.DbOperation;
import io.github.aimtone.tentacolous.sink.ChangeEvent;
import io.github.aimtone.tentacolous.sink.ChangeEventSerializers;
import io.github.aimtone.tentacolous.sink.MessageFormat;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaChangeEventSinkTest {

    @Test
    @SuppressWarnings("unchecked")
    void publishesRecordKeyedByRecordKeyWithHeaders() throws Exception {
        KafkaTemplate<String, byte[]> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        KafkaSinkProperties properties = new KafkaSinkProperties();
        properties.setTopicPrefix("cdc.");
        properties.setFormat(MessageFormat.ENVELOPE);

        KafkaChangeEventSink sink = new KafkaChangeEventSink(
                template, properties, ChangeEventSerializers.of(properties.getFormat(), null));

        ChangeEvent event = new ChangeEvent(42L, "Person", DbOperation.UPDATE, "7",
                "{\"id\":7}", "{\"id\":7}", Instant.parse("2026-09-01T12:00:00Z"));

        sink.publish(event);

        ArgumentCaptor<ProducerRecord<String, byte[]>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        org.mockito.Mockito.verify(template).send(captor.capture());
        ProducerRecord<String, byte[]> record = captor.getValue();

        assertThat(record.topic()).isEqualTo("cdc.Person");
        assertThat(record.key()).isEqualTo("7");
        assertThat(header(record, "tentacolous-event-id")).isEqualTo("42");
        assertThat(header(record, "tentacolous-operation")).isEqualTo("UPDATE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void usesFixedTopicWhenConfigured() throws Exception {
        KafkaTemplate<String, byte[]> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        KafkaSinkProperties properties = new KafkaSinkProperties();
        properties.setTopic("all-changes");

        KafkaChangeEventSink sink = new KafkaChangeEventSink(
                template, properties, ChangeEventSerializers.of(MessageFormat.RAW, null));

        sink.publish(new ChangeEvent(1L, "Order", DbOperation.INSERT, "1", "{\"id\":1}", null, Instant.now()));

        ArgumentCaptor<ProducerRecord<String, byte[]>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        org.mockito.Mockito.verify(template).send(captor.capture());
        assertThat(captor.getValue().topic()).isEqualTo("all-changes");
    }

    private String header(ProducerRecord<String, byte[]> record, String key) {
        return new String(record.headers().lastHeader(key).value(), StandardCharsets.UTF_8);
    }
}
