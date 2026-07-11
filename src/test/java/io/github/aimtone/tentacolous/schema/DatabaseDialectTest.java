package io.github.aimtone.tentacolous.schema;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseDialectTest {

    @Test
    void resolvesEverySupportedDatabaseProduct() throws Exception {
        assertResolved("PostgreSQL", PostgreSqlDialect.class);
        assertResolved("MySQL", MySqlDialect.class);
        assertResolved("MariaDB", MariaDbDialect.class);
        assertResolved("Microsoft SQL Server", SqlServerDialect.class);
        assertResolved("Oracle", OracleDialect.class);
        assertResolved("SQLite", SqliteDialect.class);
    }

    @Test
    void usesDatabaseSpecificPaginationAndBooleanLiterals() {
        assertThat(new MySqlDialect().selectPendingEventsSql("events")).endsWith("LIMIT ?");
        assertThat(new SqlServerDialect().selectPendingEventsSql("events")).startsWith("SELECT TOP (?)");
        assertThat(new OracleDialect().selectPendingEventsSql("events")).endsWith("WHERE ROWNUM <= ?");
        assertThat(new OracleDialect().trueLiteral()).isEqualTo("1");
        assertThat(new PostgreSqlDialect().trueLiteral()).isEqualTo("true");
    }

    private void assertResolved(String product, Class<? extends DatabaseDialect> expected) throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn(product);
        when(jdbc.execute(any(ConnectionCallback.class))).thenAnswer(invocation ->
                ((ConnectionCallback<?>) invocation.getArgument(0)).doInConnection(connection));

        var resolver = new DatabaseDialectResolver(jdbc, List.of(
                new PostgreSqlDialect(), new MariaDbDialect(), new MySqlDialect(), new SqlServerDialect(),
                new OracleDialect(), new SqliteDialect()
        ));
        assertThat(resolver.resolve()).isInstanceOf(expected);
    }
}
