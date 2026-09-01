package io.github.aimtone.tentacolous.config;

import io.github.aimtone.tentacolous.sink.kafka.KafkaChangeEventSink;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaSinkAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class, KafkaSinkAutoConfiguration.class));

    @Test
    void registersTheSinkAndTemplateWhenEnabled() {
        runner
                .withPropertyValues(
                        "tentacolous.kafka.enabled=true",
                        "spring.kafka.bootstrap-servers=localhost:9092")
                .run(context -> {
                    assertThat(context).hasSingleBean(KafkaChangeEventSink.class);
                    assertThat(context).hasBean("tentacolousKafkaTemplate");
                    assertThat(context.getBean(KafkaChangeEventSink.class).name()).isEqualTo("kafka");
                });
    }

    @Test
    void registersTheSinkFromPropertiesWhenNoProducerFactoryBeanExists() {
        // Mirrors a Spring Boot 4 app that added spring-kafka without Boot's Kafka auto-configuration.
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(KafkaSinkAutoConfiguration.class))
                .withPropertyValues(
                        "tentacolous.kafka.enabled=true",
                        "spring.kafka.bootstrap-servers=localhost:9092")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KafkaChangeEventSink.class);
                });
    }

    @Test
    void failsFastWhenEnabledWithNoProducerConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(KafkaSinkAutoConfiguration.class))
                .withPropertyValues("tentacolous.kafka.enabled=true")
                .run(context -> assertThat(context).getFailure()
                        .hasMessageContaining("spring.kafka.bootstrap-servers"));
    }

    @Test
    void doesNothingWhenThePropertyIsAbsent() {
        runner.run(context -> assertThat(context).doesNotHaveBean(KafkaChangeEventSink.class));
    }

    @Test
    void doesNothingWhenExplicitlyDisabled() {
        runner
                .withPropertyValues("tentacolous.kafka.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(KafkaChangeEventSink.class));
    }
}
