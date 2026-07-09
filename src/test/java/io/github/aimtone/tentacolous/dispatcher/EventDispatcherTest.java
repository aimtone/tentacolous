package io.github.aimtone.tentacolous.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aimtone.tentacolous.annotations.UponDeleting;
import io.github.aimtone.tentacolous.annotations.UponInserting;
import io.github.aimtone.tentacolous.annotations.UponUpdating;
import io.github.aimtone.tentacolous.annotations.ValueType;
import io.github.aimtone.tentacolous.config.DbListenerProperties;
import io.github.aimtone.tentacolous.model.DbChangeEvent;
import io.github.aimtone.tentacolous.registry.ListenerRegistry;
import io.github.aimtone.tentacolous.scanner.DbListenerMethodScanner;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventDispatcherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void dispatchesEventsByOperation() {
        ListenerRegistry registry = new ListenerRegistry();
        ListenerBean bean = new ListenerBean();
        new DbListenerMethodScanner(registry).postProcessAfterInitialization(bean, "listenerBean");

        EventDispatcher dispatcher = new EventDispatcher(registry, objectMapper);

        dispatcher.dispatch(event("INSERT", "{\"id\":1,\"status\":\"PENDIENTE\",\"active\":true,\"level\":1}"));
        dispatcher.dispatch(event("UPDATE", "{\"id\":1,\"status\":\"APROBADO\",\"active\":true,\"level\":1}"));
        dispatcher.dispatch(event("DELETE", "{\"id\":1,\"status\":\"APROBADO\",\"active\":false,\"level\":1}"));

        assertThat(bean.insertCount).isEqualTo(1);
        assertThat(bean.updateCount).isEqualTo(1);
        assertThat(bean.deleteCount).isEqualTo(1);
        assertThat(bean.lastUpdated.status).isEqualTo("APROBADO");
    }

    @Test
    void skipsListenerWhenFilterDoesNotMatch() {
        ListenerRegistry registry = new ListenerRegistry();
        ListenerBean bean = new ListenerBean();
        new DbListenerMethodScanner(registry).postProcessAfterInitialization(bean, "listenerBean");

        EventDispatcher dispatcher = new EventDispatcher(registry, objectMapper);

        dispatcher.dispatch(event("UPDATE", "{\"id\":1,\"status\":\"RECHAZADO\",\"active\":true,\"level\":1}"));

        assertThat(bean.updateCount).isZero();
    }

    @Test
    void dispatchesUpdateWithOldEntityWhenListenerAcceptsTwoParameters() {
        ListenerRegistry registry = new ListenerRegistry();
        UpdateWithOldEntityBean bean = new UpdateWithOldEntityBean();
        new DbListenerMethodScanner(registry).postProcessAfterInitialization(bean, "updateWithOldEntityBean");

        EventDispatcher dispatcher = new EventDispatcher(registry, objectMapper);
        DbChangeEvent event = event("UPDATE", "{\"id\":1,\"status\":\"APROBADO\",\"active\":true,\"level\":2}");
        event.setOldPayload("{\"id\":1,\"status\":\"PENDIENTE\",\"active\":true,\"level\":1}");

        dispatcher.dispatch(event);

        assertThat(bean.newUser.status).isEqualTo("APROBADO");
        assertThat(bean.newUser.level).isEqualTo(2);
        assertThat(bean.oldUser.status).isEqualTo("PENDIENTE");
        assertThat(bean.oldUser.level).isEqualTo(1);
    }

    @Test
    void dispatchesUpdateWithHistoryWhenListenerAcceptsThreeParameters() {
        ListenerRegistry registry = new ListenerRegistry();
        UpdateWithHistoryBean bean = new UpdateWithHistoryBean();
        new DbListenerMethodScanner(registry).postProcessAfterInitialization(bean, "updateWithHistoryBean");

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                eq("User"),
                eq(3L),
                eq("1"),
                eq("id"),
                eq("1")
        )).thenReturn(List.of(
                "{\"id\":1,\"status\":\"CREADO\",\"active\":true,\"level\":0}",
                "{\"id\":1,\"status\":\"PENDIENTE\",\"active\":true,\"level\":1}"
        ));

        EventDispatcher dispatcher = new EventDispatcher(registry, objectMapper, jdbcTemplate, new DbListenerProperties());
        DbChangeEvent event = event("UPDATE", "{\"id\":1,\"status\":\"APROBADO\",\"active\":true,\"level\":2}");
        event.setId(3L);
        event.setRecordKey("1");
        event.setOldPayload("{\"id\":1,\"status\":\"PENDIENTE\",\"active\":true,\"level\":1}");

        dispatcher.dispatch(event);

        assertThat(bean.newUser.status).isEqualTo("APROBADO");
        assertThat(bean.oldUser.status).isEqualTo("PENDIENTE");
        assertThat(bean.history).extracting(user -> user.status).containsExactly("CREADO", "PENDIENTE");
    }

    @Test
    void dispatchesDeleteWithHistoryWhenListenerAcceptsTwoParameters() {
        ListenerRegistry registry = new ListenerRegistry();
        DeleteWithHistoryBean bean = new DeleteWithHistoryBean();
        new DbListenerMethodScanner(registry).postProcessAfterInitialization(bean, "deleteWithHistoryBean");

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                eq("User"),
                eq(4L),
                eq("1"),
                eq("id"),
                eq("1")
        )).thenReturn(List.of(
                "{\"id\":1,\"status\":\"CREADO\",\"active\":true,\"level\":0}",
                "{\"id\":1,\"status\":\"APROBADO\",\"active\":true,\"level\":2}"
        ));

        EventDispatcher dispatcher = new EventDispatcher(registry, objectMapper, jdbcTemplate, new DbListenerProperties());
        DbChangeEvent event = event("DELETE", "{\"id\":1,\"status\":\"APROBADO\",\"active\":false,\"level\":2}");
        event.setId(4L);
        event.setRecordKey("1");

        dispatcher.dispatch(event);

        assertThat(bean.deletedUser.status).isEqualTo("APROBADO");
        assertThat(bean.history).extracting(user -> user.status).containsExactly("CREADO", "APROBADO");
    }

    @Test
    void supportsBooleanAndNumberFilters() {
        ListenerRegistry registry = new ListenerRegistry();
        FilterBean bean = new FilterBean();
        new DbListenerMethodScanner(registry).postProcessAfterInitialization(bean, "filterBean");

        EventDispatcher dispatcher = new EventDispatcher(registry, objectMapper);

        dispatcher.dispatch(event("INSERT", "{\"id\":1,\"status\":\"A\",\"active\":true,\"level\":1}"));
        dispatcher.dispatch(event("INSERT", "{\"id\":2,\"status\":\"A\",\"active\":false,\"level\":1}"));
        dispatcher.dispatch(event("INSERT", "{\"id\":3,\"status\":\"A\",\"active\":true,\"level\":2}"));

        assertThat(bean.activeCount).isEqualTo(2);
        assertThat(bean.levelOneCount).isEqualTo(2);
    }

    @Test
    void supportsTypedStringValueFilters() {
        ListenerRegistry registry = new ListenerRegistry();
        TypedFilterBean bean = new TypedFilterBean();
        new DbListenerMethodScanner(registry).postProcessAfterInitialization(bean, "typedFilterBean");

        EventDispatcher dispatcher = new EventDispatcher(registry, objectMapper);

        dispatcher.dispatch(event("INSERT", "{"
                + "\"id\":1,"
                + "\"integerValue\":7,"
                + "\"longValue\":99,"
                + "\"decimalValue\":\"10.50\","
                + "\"doubleValue\":3.14,"
                + "\"dateValue\":\"2026-07-06\","
                + "\"timeValue\":\"13:45:00\","
                + "\"dateTimeValue\":\"2026-07-06T13:45:00Z\","
                + "\"uuidValue\":\"550e8400-e29b-41d4-a716-446655440000\","
                + "\"active\":true"
                + "}"));

        assertThat(bean.integerCount).isEqualTo(1);
        assertThat(bean.longCount).isEqualTo(1);
        assertThat(bean.decimalCount).isEqualTo(1);
        assertThat(bean.doubleCount).isEqualTo(1);
        assertThat(bean.dateCount).isEqualTo(1);
        assertThat(bean.timeCount).isEqualTo(1);
        assertThat(bean.dateTimeCount).isEqualTo(1);
        assertThat(bean.uuidCount).isEqualTo(1);
        assertThat(bean.booleanViaValueCount).isEqualTo(1);
    }

    private DbChangeEvent event(String operation, String payload) {
        DbChangeEvent event = new DbChangeEvent();
        event.setId(1L);
        event.setEntityName("User");
        event.setOperation(operation);
        event.setPayload(payload);
        return event;
    }

    static class ListenerBean {
        int insertCount;
        int updateCount;
        int deleteCount;
        User lastUpdated;

        @UponInserting(entity = User.class, entityName = "User")
        public void onInserting(User user) {
            insertCount++;
        }

        @UponUpdating(
                entity = User.class,
                entityName = "User",
                field = "status",
                valueType = ValueType.STRING,
                value = "APROBADO"
        )
        public void onUpdating(User user) {
            updateCount++;
            lastUpdated = user;
        }

        @UponDeleting(entity = User.class, entityName = "User")
        public void onDeleting(User user) {
            deleteCount++;
        }
    }

    static class FilterBean {
        int activeCount;
        int levelOneCount;

        @UponInserting(
                entity = User.class,
                entityName = "User",
                field = "active",
                valueType = ValueType.BOOLEAN,
                value = "true"
        )
        public void onActive(User user) {
            activeCount++;
        }

        @UponInserting(
                entity = User.class,
                entityName = "User",
                field = "level",
                valueType = ValueType.NUMBER,
                value = "1"
        )
        public void onLevelOne(User user) {
            levelOneCount++;
        }
    }

    static class UpdateWithOldEntityBean {
        User newUser;
        User oldUser;

        @UponUpdating(entity = User.class, entityName = "User")
        public void onUpdating(User newUser, User oldUser) {
            this.newUser = newUser;
            this.oldUser = oldUser;
        }
    }

    static class UpdateWithHistoryBean {
        User newUser;
        User oldUser;
        List<User> history;

        @UponUpdating(entity = User.class, entityName = "User")
        public void onUpdating(User newUser, User oldUser, List<User> history) {
            this.newUser = newUser;
            this.oldUser = oldUser;
            this.history = history;
        }
    }

    static class DeleteWithHistoryBean {
        User deletedUser;
        List<User> history;

        @UponDeleting(entity = User.class, entityName = "User")
        public void onDeleting(User deletedUser, List<User> history) {
            this.deletedUser = deletedUser;
            this.history = history;
        }
    }

    static class TypedFilterBean {
        int integerCount;
        int longCount;
        int decimalCount;
        int doubleCount;
        int dateCount;
        int timeCount;
        int dateTimeCount;
        int uuidCount;
        int booleanViaValueCount;

        @UponInserting(entity = User.class, entityName = "User", field = "integerValue", valueType = ValueType.INTEGER, value = "7")
        public void onInteger(User user) {
            integerCount++;
        }

        @UponInserting(entity = User.class, entityName = "User", field = "longValue", valueType = ValueType.LONG, value = "99")
        public void onLong(User user) {
            longCount++;
        }

        @UponInserting(entity = User.class, entityName = "User", field = "decimalValue", valueType = ValueType.DECIMAL, value = "10.500")
        public void onDecimal(User user) {
            decimalCount++;
        }

        @UponInserting(entity = User.class, entityName = "User", field = "doubleValue", valueType = ValueType.DOUBLE, value = "3.14")
        public void onDouble(User user) {
            doubleCount++;
        }

        @UponInserting(entity = User.class, entityName = "User", field = "dateValue", valueType = ValueType.DATE, value = "2026-07-06")
        public void onDate(User user) {
            dateCount++;
        }

        @UponInserting(entity = User.class, entityName = "User", field = "timeValue", valueType = ValueType.TIME, value = "13:45:00")
        public void onTime(User user) {
            timeCount++;
        }

        @UponInserting(entity = User.class, entityName = "User", field = "dateTimeValue", valueType = ValueType.DATETIME, value = "2026-07-06T13:45:00Z")
        public void onDateTime(User user) {
            dateTimeCount++;
        }

        @UponInserting(entity = User.class, entityName = "User", field = "uuidValue", valueType = ValueType.UUID, value = "550e8400-e29b-41d4-a716-446655440000")
        public void onUuid(User user) {
            uuidCount++;
        }

        @UponInserting(entity = User.class, entityName = "User", field = "active", valueType = ValueType.BOOLEAN, value = "true")
        public void onBooleanViaValue(User user) {
            booleanViaValueCount++;
        }
    }

    public static class User {
        public long id;
        public String status;
        public boolean active;
        public long level;
        public int integerValue;
        public long longValue;
        public String decimalValue;
        public double doubleValue;
        public String dateValue;
        public String timeValue;
        public String dateTimeValue;
        public String uuidValue;
    }
}
