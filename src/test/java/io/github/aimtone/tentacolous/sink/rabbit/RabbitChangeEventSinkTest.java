package io.github.aimtone.tentacolous.sink.rabbit;

import io.github.aimtone.tentacolous.model.DbOperation;
import io.github.aimtone.tentacolous.sink.ChangeEvent;
import io.github.aimtone.tentacolous.sink.ChangeEventSerializers;
import io.github.aimtone.tentacolous.sink.MessageFormat;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitChangeEventSinkTest {

    @Test
    void publishesToTopicExchangeWithEntityOperationRoutingKey() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        RabbitSinkProperties properties = new RabbitSinkProperties();
        properties.setExchange("tentacolous");

        RabbitChangeEventSink sink = new RabbitChangeEventSink(
                template, properties, ChangeEventSerializers.of(MessageFormat.ENVELOPE, null));

        ChangeEvent event = new ChangeEvent(5L, "Person", DbOperation.DELETE, "7",
                "{\"id\":7}", null, Instant.parse("2026-09-01T12:00:00Z"));

        sink.publish(event);

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(template).send(org.mockito.ArgumentMatchers.eq("tentacolous"),
                org.mockito.ArgumentMatchers.eq("person.delete"), message.capture());

        assertThat(message.getValue().getMessageProperties().getMessageId()).isEqualTo("5");
        assertThat(message.getValue().getMessageProperties().getHeaders())
                .containsEntry("tentacolous-operation", "DELETE");
    }
}
