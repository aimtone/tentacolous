package com.aimtone.tentacolous.config;

import com.aimtone.tentacolous.annotations.UponInserting;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aimtone.tentacolous.dispatcher.EventDispatcher;
import com.aimtone.tentacolous.poller.DbChangeEventPoller;
import com.aimtone.tentacolous.registry.ListenerRegistry;
import com.aimtone.tentacolous.schema.DbListenerSchemaManager;
import com.aimtone.tentacolous.scanner.DbListenerMethodScanner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.TaskScheduler;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DbListenerAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DbListenerAutoConfiguration.class));

    @Test
    void createsCoreBeansWithoutDataSource() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ListenerRegistry.class);
            assertThat(context).hasSingleBean(DbListenerMethodScanner.class);
            assertThat(context).hasSingleBean(EventDispatcher.class);
            assertThat(context).hasSingleBean(ObjectMapper.class);
            assertThat(context).doesNotHaveBean(DbChangeEventPoller.class);
            assertThat(context).doesNotHaveBean(DbListenerSchemaManager.class);
        });
    }

    @Test
    void createsJdbcBeansWhenDataSourceExists() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyInt())).thenReturn(Collections.emptyList());

        contextRunner
                .withBean(JdbcTemplate.class, () -> jdbcTemplate)
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .withPropertyValues("tentacolous.schema-management=none")
                .run(context -> {
                    assertThat(context).hasSingleBean(DbListenerSchemaManager.class);
                    assertThat(context).hasSingleBean(DbChangeEventPoller.class);
                });
    }

    @Test
    void createsJdbcInfrastructureFromDataSource() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        doReturn(scheduledFuture)
                .when(taskScheduler)
                .scheduleWithFixedDelay(any(Runnable.class), any(Instant.class), any(Duration.class));

        contextRunner
                .withBean(DataSource.class, () -> dataSource)
                .withBean("dbListenerTaskScheduler", TaskScheduler.class, () -> taskScheduler)
                .withPropertyValues("tentacolous.schema-management=none")
                .run(context -> {
                    assertThat(context).hasSingleBean(JdbcTemplate.class);
                    assertThat(context).hasSingleBean(DbListenerSchemaManager.class);
                    assertThat(context).hasSingleBean(DbChangeEventPoller.class);
                });
    }

    @Test
    void createsTriggersForAnnotatedBeansWhenApplicationStarts() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);

        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(jdbcTemplate.execute(any(org.springframework.jdbc.core.ConnectionCallback.class))).thenAnswer(invocation -> {
            org.springframework.jdbc.core.ConnectionCallback<?> callback = invocation.getArgument(0);
            return callback.doInConnection(connection);
        });
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyInt())).thenReturn(Collections.emptyList());
        doReturn(scheduledFuture)
                .when(taskScheduler)
                .scheduleWithFixedDelay(any(Runnable.class), any(Instant.class), any(Duration.class));

        contextRunner
                .withBean(JdbcTemplate.class, () -> jdbcTemplate)
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .withBean("dbListenerTaskScheduler", TaskScheduler.class, () -> taskScheduler)
                .withBean(PersonListener.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(DbChangeEventPoller.class);
                    verify(jdbcTemplate).execute("CREATE TRIGGER person_tentacolous_listener_insert "
                            + "AFTER INSERT ON person "
                            + "FOR EACH ROW "
                            + "EXECUTE FUNCTION db_change_event_notify_change('Person', '{}')");
                    verify(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), any(Instant.class), eq(Duration.ofSeconds(1)));
                });
    }

    @Test
    void backsOffPollerWhenDisabled() {
        contextRunner
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .withPropertyValues("tentacolous.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DbChangeEventPoller.class));
    }

    static class PersonListener {

        @UponInserting(entity = Person.class)
        public void onInserting(Person person) {
        }
    }

    static class Person {
    }
}
