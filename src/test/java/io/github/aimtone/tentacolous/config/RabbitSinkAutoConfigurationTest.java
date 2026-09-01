package io.github.aimtone.tentacolous.config;

import io.github.aimtone.tentacolous.sink.rabbit.RabbitChangeEventSink;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitSinkAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class, RabbitSinkAutoConfiguration.class));

    @Test
    void registersTheSinkAndExchangeWhenEnabled() {
        runner
                .withPropertyValues("tentacolous.rabbitmq.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(RabbitChangeEventSink.class);
                    assertThat(context).hasSingleBean(TopicExchange.class);
                    assertThat(context.getBean(TopicExchange.class).getName()).isEqualTo("tentacolous");
                });
    }

    @Test
    void doesNothingWhenThePropertyIsAbsent() {
        runner.run(context -> assertThat(context).doesNotHaveBean(RabbitChangeEventSink.class));
    }

    @Test
    void doesNothingWhenExplicitlyDisabled() {
        runner
                .withPropertyValues("tentacolous.rabbitmq.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RabbitChangeEventSink.class));
    }

    @Test
    void canSkipTheExchangeDeclaration() {
        runner
                .withPropertyValues(
                        "tentacolous.rabbitmq.enabled=true",
                        "tentacolous.rabbitmq.declare-exchange=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(RabbitChangeEventSink.class);
                    assertThat(context).doesNotHaveBean(TopicExchange.class);
                });
    }
}
