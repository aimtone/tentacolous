package io.github.aimtone.tentacolous.sink.kafka;

import io.github.aimtone.tentacolous.sink.ChangeEvent;
import io.github.aimtone.tentacolous.sink.ChangeEventSerializer;
import io.github.aimtone.tentacolous.sink.ChangeEventSink;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Publishes every change to Kafka, one record per event, keyed by the record key for per-row ordering. */
public class KafkaChangeEventSink implements ChangeEventSink {

    private static final Logger log = LoggerFactory.getLogger(KafkaChangeEventSink.class);

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final KafkaSinkProperties properties;
    private final ChangeEventSerializer serializer;

    public KafkaChangeEventSink(
            KafkaTemplate<String, byte[]> kafkaTemplate,
            KafkaSinkProperties properties,
            ChangeEventSerializer serializer
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.serializer = serializer;
    }

    @Override
    public String name() {
        return "kafka";
    }

    @Override
    public void publish(ChangeEvent event) throws Exception {
        String topic = properties.resolveTopic(event.getEntityName());
        byte[] body = serializer.serialize(event);

        ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, event.getRecordKey(), body);

        if (properties.isAddHeaders()) {
            record.headers().add("tentacolous-event-id", utf8(Long.toString(event.getEventId())));
            record.headers().add("tentacolous-entity", utf8(nullToEmpty(event.getEntityName())));
            record.headers().add("tentacolous-operation", utf8(event.getOperation().name()));
        }

        kafkaTemplate.send(record).get(properties.getSendTimeout().toMillis(), TimeUnit.MILLISECONDS);

        if (log.isDebugEnabled()) {
            log.debug("Published {} event {} for entity {} to topic {}",
                    event.getOperation(), event.getEventId(), event.getEntityName(), topic);
        }
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
