package com.aimtone.tentacolous.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aimtone.tentacolous.dispatcher.EventDispatcher;
import com.aimtone.tentacolous.poller.DbChangeEventPoller;
import com.aimtone.tentacolous.registry.ListenerRegistry;
import com.aimtone.tentacolous.schema.DbListenerSchemaManager;
import com.aimtone.tentacolous.scanner.DbListenerMethodScanner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.sql.DataSource;

@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration"
})
@ConditionalOnClass(JdbcTemplate.class)
@EnableConfigurationProperties(DbListenerProperties.class)
public class DbListenerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ListenerRegistry listenerRegistry() {
        return new ListenerRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public static DbListenerMethodScanner dbListenerMethodScanner(ListenerRegistry listenerRegistry) {
        return new DbListenerMethodScanner(listenerRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public EventDispatcher eventDispatcher(
            ListenerRegistry listenerRegistry,
            ObjectMapper objectMapper
    ) {
        return new EventDispatcher(listenerRegistry, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public DbListenerSchemaManager dbListenerSchemaManager(
            JdbcTemplate jdbcTemplate,
            ListenerRegistry listenerRegistry,
            DbListenerProperties properties
    ) {
        return new DbListenerSchemaManager(jdbcTemplate, listenerRegistry, properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "dbListenerTaskScheduler")
    public TaskScheduler dbListenerTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("tentacolous-");
        scheduler.setPoolSize(1);
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "tentacolous", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DbChangeEventPoller dbChangeEventPoller(
            JdbcTemplate jdbcTemplate,
            EventDispatcher eventDispatcher,
            ListenerRegistry listenerRegistry,
            DbListenerProperties properties,
            DbListenerSchemaManager schemaManager,
            TaskScheduler taskScheduler
    ) {
        return new DbChangeEventPoller(
                jdbcTemplate,
                eventDispatcher,
                listenerRegistry,
                properties,
                schemaManager,
                taskScheduler
        );
    }
}
