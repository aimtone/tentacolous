package io.github.aimtone.tentacolous.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import io.github.aimtone.tentacolous.config.DbListenerAutoConfiguration;
import io.github.aimtone.tentacolous.config.RabbitSinkAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers(disabledWithoutDocker = true)
class RabbitSinkIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void forwardsADatabaseUpdateToRabbitAsAnEnvelope() throws Exception {
        try (HikariDataSource setup = dataSource();
             var connection = setup.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE person (id BIGSERIAL PRIMARY KEY, name VARCHAR(255), status VARCHAR(50))");
        }

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        RabbitAutoConfiguration.class,
                        DbListenerAutoConfiguration.class,
                        RabbitSinkAutoConfiguration.class))
                .withBean(DataSource.class, this::dataSource)
                .withUserConfiguration(PersonCaptureConfig.class, ConsumerQueueConfig.class)
                .withPropertyValues(
                        "spring.rabbitmq.host=" + RABBIT.getHost(),
                        "spring.rabbitmq.port=" + RABBIT.getAmqpPort(),
                        "spring.rabbitmq.username=" + RABBIT.getAdminUsername(),
                        "spring.rabbitmq.password=" + RABBIT.getAdminPassword(),
                        "tentacolous.rabbitmq.enabled=true",
                        "tentacolous.poll-interval=200ms")
                .run(context -> {
                    JdbcTemplate jdbcTemplate = new JdbcTemplate(context.getBean(DataSource.class));
                    jdbcTemplate.update("INSERT INTO person(name, status) VALUES (?, ?)", "Ana", "NEW");
                    jdbcTemplate.update("UPDATE person SET status = ? WHERE name = ?", "APPROVED", "Ana");

                    RabbitTemplate rabbitTemplate = context.getBean(RabbitTemplate.class);
                    JsonNode updateEnvelope = awaitEnvelope(rabbitTemplate, "UPDATE");

                    assertThat(updateEnvelope.get("entity").asText()).isEqualTo("Person");
                    assertThat(updateEnvelope.get("before").get("status").asText()).isEqualTo("NEW");
                    assertThat(updateEnvelope.get("after").get("status").asText()).isEqualTo("APPROVED");
                });
    }

    private JsonNode awaitEnvelope(RabbitTemplate rabbitTemplate, String operation) {
        AtomicReference<JsonNode> match = new AtomicReference<>();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Message message = rabbitTemplate.receive("it.person", 500);
            if (message != null) {
                JsonNode envelope = objectMapper.readTree(message.getBody());
                if (operation.equals(envelope.get("operation").asText())) {
                    match.set(envelope);
                }
            }
            assertThat(match.get()).isNotNull();
        });

        return match.get();
    }

    private HikariDataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        dataSource.setMaximumPoolSize(3);
        return dataSource;
    }

    @Configuration
    static class ConsumerQueueConfig {

        @Bean
        Queue itPersonQueue() {
            return QueueBuilder.durable("it.person").build();
        }

        @Bean
        Binding itPersonBinding() {
            return new Binding("it.person", Binding.DestinationType.QUEUE, "tentacolous", "person.*", null);
        }
    }
}
