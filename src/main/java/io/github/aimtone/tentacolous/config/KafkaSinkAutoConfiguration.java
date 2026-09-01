package io.github.aimtone.tentacolous.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aimtone.tentacolous.sink.ChangeEventSerializers;
import io.github.aimtone.tentacolous.sink.kafka.KafkaChangeEventSink;
import io.github.aimtone.tentacolous.sink.kafka.KafkaSinkProperties;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Registers the Kafka {@link io.github.aimtone.tentacolous.sink.ChangeEventSink}.
 *
 * <p>The producer configuration is taken, in order of preference, from a Spring-managed
 * {@link ProducerFactory} (auto-configured by Spring Boot when its Kafka support is present) or from
 * the {@code spring.kafka.*} properties directly. Nothing in this class references a
 * Boot-version-specific type, so it works on both Spring Boot 3.x and 4.x.
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration"
})
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "tentacolous.kafka", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(KafkaSinkProperties.class)
public class KafkaSinkAutoConfiguration {

    @Bean(name = "tentacolousKafkaTemplate")
    @ConditionalOnMissingBean(name = "tentacolousKafkaTemplate")
    public KafkaTemplate<String, byte[]> tentacolousKafkaTemplate(
            Environment environment,
            ObjectProvider<ProducerFactory<?, ?>> producerFactory
    ) {
        Map<String, Object> configs = new HashMap<>();
        ProducerFactory<?, ?> existing = producerFactory.getIfAvailable();

        if (existing != null) {
            configs.putAll(existing.getConfigurationProperties());
        } else {
            configs.putAll(kafkaPropertiesFromEnvironment(environment));
        }

        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        configs.putIfAbsent(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configs.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");

        if (configs.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG) == null) {
            throw new IllegalStateException(
                    "tentacolous.kafka.enabled=true but no Kafka producer configuration was found. "
                            + "Provide a ProducerFactory bean or set spring.kafka.bootstrap-servers.");
        }

        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(configs));
    }

    private Map<String, Object> kafkaPropertiesFromEnvironment(Environment environment) {
        Map<String, Object> configs = new HashMap<>();
        Binder binder = Binder.get(environment);

        binder.bind("spring.kafka.bootstrap-servers", Bindable.listOf(String.class))
                .ifBound(servers -> configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", servers)));
        binder.bind("spring.kafka.properties", Bindable.mapOf(String.class, String.class))
                .ifBound(configs::putAll);
        binder.bind("spring.kafka.producer.properties", Bindable.mapOf(String.class, String.class))
                .ifBound(configs::putAll);

        return configs;
    }

    @Bean
    @ConditionalOnBean(name = "tentacolousKafkaTemplate")
    @ConditionalOnMissingBean
    public KafkaChangeEventSink kafkaChangeEventSink(
            @Qualifier("tentacolousKafkaTemplate") KafkaTemplate<String, byte[]> tentacolousKafkaTemplate,
            KafkaSinkProperties properties,
            ObjectProvider<ObjectMapper> objectMapper
    ) {
        return new KafkaChangeEventSink(
                tentacolousKafkaTemplate,
                properties,
                ChangeEventSerializers.of(properties.getFormat(), objectMapper.getIfAvailable(ObjectMapper::new))
        );
    }
}
