package io.github.aimtone.tentacolous.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aimtone.tentacolous.sink.ChangeEventSerializers;
import io.github.aimtone.tentacolous.sink.rabbit.RabbitChangeEventSink;
import io.github.aimtone.tentacolous.sink.rabbit.RabbitSinkProperties;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
        "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration"
})
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnProperty(prefix = "tentacolous.rabbitmq", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RabbitSinkProperties.class)
public class RabbitSinkAutoConfiguration {

    @Bean
    @ConditionalOnBean(RabbitTemplate.class)
    @ConditionalOnMissingBean
    public RabbitChangeEventSink rabbitChangeEventSink(
            RabbitTemplate rabbitTemplate,
            RabbitSinkProperties properties,
            ObjectProvider<ObjectMapper> objectMapper
    ) {
        return new RabbitChangeEventSink(
                rabbitTemplate,
                properties,
                ChangeEventSerializers.of(properties.getFormat(), objectMapper.getIfAvailable(ObjectMapper::new))
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "tentacolous.rabbitmq", name = "declare-exchange", havingValue = "true", matchIfMissing = true)
    public TopicExchange tentacolousRabbitExchange(RabbitSinkProperties properties) {
        return ExchangeBuilder.topicExchange(properties.getExchange()).durable(true).build();
    }
}
