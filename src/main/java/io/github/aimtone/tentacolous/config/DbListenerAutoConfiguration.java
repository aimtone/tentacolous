package io.github.aimtone.tentacolous.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aimtone.tentacolous.dispatcher.EventDispatcher;
import io.github.aimtone.tentacolous.poller.DbChangeEventPoller;
import io.github.aimtone.tentacolous.registry.ListenerRegistry;
import io.github.aimtone.tentacolous.schema.DbListenerSchemaManager;
import io.github.aimtone.tentacolous.schema.DatabaseDialect;
import io.github.aimtone.tentacolous.schema.DatabaseDialectResolver;
import io.github.aimtone.tentacolous.schema.*;
import io.github.aimtone.tentacolous.scanner.DbListenerMethodScanner;
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
import java.util.List;

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
            ObjectMapper objectMapper,
            org.springframework.beans.factory.ObjectProvider<JdbcTemplate> jdbcTemplate,
            DbListenerProperties properties,
            org.springframework.beans.factory.ObjectProvider<DatabaseDialectResolver> dialectResolver
    ) {
        return new EventDispatcher(listenerRegistry, objectMapper, jdbcTemplate.getIfAvailable(), properties,
                dialectResolver.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(PostgreSqlDialect.class)
    public PostgreSqlDialect postgreSqlDialect() {
        return new PostgreSqlDialect();
    }

    @Bean
    @ConditionalOnMissingBean(MySqlDialect.class)
    public MySqlDialect mySqlDialect() { return new MySqlDialect(); }

    @Bean
    @ConditionalOnMissingBean(MariaDbDialect.class)
    public MariaDbDialect mariaDbDialect() { return new MariaDbDialect(); }

    @Bean
    @ConditionalOnMissingBean(SqlServerDialect.class)
    public SqlServerDialect sqlServerDialect() { return new SqlServerDialect(); }

    @Bean
    @ConditionalOnMissingBean(OracleDialect.class)
    public OracleDialect oracleDialect() { return new OracleDialect(); }

    @Bean
    @ConditionalOnMissingBean(SqliteDialect.class)
    public SqliteDialect sqliteDialect() { return new SqliteDialect(); }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public DatabaseDialectResolver databaseDialectResolver(
            JdbcTemplate jdbcTemplate,
            List<DatabaseDialect> dialects
    ) {
        return new DatabaseDialectResolver(jdbcTemplate, dialects);
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
            DbListenerProperties properties,
            DatabaseDialectResolver dialectResolver
    ) {
        return new DbListenerSchemaManager(jdbcTemplate, listenerRegistry, properties, dialectResolver);
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
            TaskScheduler taskScheduler,
            DatabaseDialectResolver dialectResolver
    ) {
        return new DbChangeEventPoller(
                jdbcTemplate,
                eventDispatcher,
                listenerRegistry,
                properties,
                schemaManager,
                taskScheduler,
                dialectResolver
        );
    }
}
