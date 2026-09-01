package io.github.aimtone.tentacolous.sink.rabbit;

import io.github.aimtone.tentacolous.sink.ChangeEvent;
import io.github.aimtone.tentacolous.sink.ChangeEventSerializer;
import io.github.aimtone.tentacolous.sink.ChangeEventSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** Publishes every change to a RabbitMQ topic exchange, routed by {@code entity.operation}. */
public class RabbitChangeEventSink implements ChangeEventSink {

    private static final Logger log = LoggerFactory.getLogger(RabbitChangeEventSink.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitSinkProperties properties;
    private final ChangeEventSerializer serializer;

    public RabbitChangeEventSink(
            RabbitTemplate rabbitTemplate,
            RabbitSinkProperties properties,
            ChangeEventSerializer serializer
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.serializer = serializer;
    }

    @Override
    public String name() {
        return "rabbitmq";
    }

    @Override
    public void publish(ChangeEvent event) {
        String routingKey = properties.resolveRoutingKey(event.getEntityName(), event.getOperation().name());
        byte[] body = serializer.serialize(event);

        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(serializer.contentType());
        messageProperties.setContentEncoding("UTF-8");
        messageProperties.setMessageId(Long.toString(event.getEventId()));
        messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);

        if (properties.isAddHeaders()) {
            messageProperties.setHeader("tentacolous-event-id", event.getEventId());
            messageProperties.setHeader("tentacolous-entity", event.getEntityName());
            messageProperties.setHeader("tentacolous-operation", event.getOperation().name());
        }

        rabbitTemplate.send(properties.getExchange(), routingKey, new Message(body, messageProperties));

        if (log.isDebugEnabled()) {
            log.debug("Published {} event {} for entity {} to exchange {} with routing key {}",
                    event.getOperation(), event.getEventId(), event.getEntityName(),
                    properties.getExchange(), routingKey);
        }
    }
}
