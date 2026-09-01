package io.github.aimtone.tentacolous.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import io.github.aimtone.tentacolous.config.DbListenerAutoConfiguration;
import io.github.aimtone.tentacolous.config.KafkaSinkAutoConfiguration;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers(disabledWithoutDocker = true)
class KafkaSinkIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void forwardsADatabaseInsertToKafkaAsAnEnvelope() throws Exception {
        try (HikariDataSource setup = dataSource();
             var connection = setup.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE person (id BIGSERIAL PRIMARY KEY, name VARCHAR(255), status VARCHAR(50))");
        }

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KafkaAutoConfiguration.class,
                        DbListenerAutoConfiguration.class,
                        KafkaSinkAutoConfiguration.class))
                .withBean(DataSource.class, this::dataSource)
                .withUserConfiguration(PersonCaptureConfig.class)
                .withPropertyValues(
                        "spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
                        "tentacolous.kafka.enabled=true",
                        "tentacolous.kafka.topic-prefix=cdc.",
                        "tentacolous.poll-interval=200ms")
                .run(context -> {
                    JdbcTemplate jdbcTemplate = new JdbcTemplate(context.getBean(DataSource.class));
                    jdbcTemplate.update("INSERT INTO person(name, status) VALUES (?, ?)", "Ana", "NEW");

                    try (KafkaConsumer<String, byte[]> consumer = consumer()) {
                        consumer.subscribe(List.of("cdc.Person"));
                        JsonNode envelope = awaitFirstEnvelope(consumer);

                        assertThat(envelope.get("entity").asText()).isEqualTo("Person");
                        assertThat(envelope.get("operation").asText()).isEqualTo("INSERT");
                        assertThat(envelope.get("recordKey").asText()).isEqualTo("1");
                        assertThat(envelope.get("before").isNull()).isTrue();
                        assertThat(envelope.get("after").get("name").asText()).isEqualTo("Ana");
                    }
                });
    }

    private JsonNode awaitFirstEnvelope(KafkaConsumer<String, byte[]> consumer) {
        List<byte[]> received = new ArrayList<>();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, byte[]> record : records) {
                received.add(record.value());
            }
            assertThat(received).isNotEmpty();
        });

        try {
            return objectMapper.readTree(received.get(0));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private HikariDataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        dataSource.setMaximumPoolSize(3);
        return dataSource;
    }

    private KafkaConsumer<String, byte[]> consumer() {
        Properties properties = new Properties();
        properties.putAll(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "integration-test",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class));
        return new KafkaConsumer<>(properties);
    }
}
