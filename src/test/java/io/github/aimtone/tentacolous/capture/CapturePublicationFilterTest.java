package io.github.aimtone.tentacolous.capture;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aimtone.tentacolous.annotations.ValueType;
import io.github.aimtone.tentacolous.filter.TentacolousFilter;
import io.github.aimtone.tentacolous.filter.TentacolousFilterContext;
import io.github.aimtone.tentacolous.model.DbChangeEvent;
import io.github.aimtone.tentacolous.model.DbOperation;
import io.github.aimtone.tentacolous.registry.CaptureRegistry;
import io.github.aimtone.tentacolous.registry.ListenerDefinition;
import io.github.aimtone.tentacolous.registry.ListenerFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CapturePublicationFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void allowsEverythingWhenNoCaptureIsDeclaredForTheEntity() {
        CapturePublicationFilter filter = new CapturePublicationFilter(new CaptureRegistry(), objectMapper);

        assertThat(filter.allows(event("Person", "{\"status\":\"NEW\"}"), DbOperation.INSERT)).isTrue();
    }

    @Test
    void appliesTheDeclarativeFilterOfAMatchingCapture() {
        CaptureRegistry registry = new CaptureRegistry();
        registry.register(capture(DbOperation.UPDATE, "Person",
                new ListenerFilter("status", ValueType.STRING, "APPROVED"), null));

        CapturePublicationFilter filter = new CapturePublicationFilter(registry, objectMapper);

        assertThat(filter.allows(event("Person", "{\"status\":\"APPROVED\"}"), DbOperation.UPDATE)).isTrue();
        assertThat(filter.allows(event("Person", "{\"status\":\"PENDING\"}"), DbOperation.UPDATE)).isFalse();
    }

    @Test
    void appliesAProgrammaticFilterOfAMatchingCapture() {
        CaptureRegistry registry = new CaptureRegistry();
        registry.register(capture(DbOperation.INSERT, "Person",
                new ListenerFilter(null, ValueType.NONE, null),
                new TentacolousFilter<Object>() {
                    @Override
                    public boolean accept(TentacolousFilterContext<Object> context) {
                        return context.getEntityName().equals("Person");
                    }
                }));

        CapturePublicationFilter filter = new CapturePublicationFilter(registry, objectMapper);

        assertThat(filter.allows(event("Person", "{\"id\":1}"), DbOperation.INSERT)).isTrue();
    }

    @Test
    void publishesWhenAnyOfSeveralCapturesAccepts() {
        CaptureRegistry registry = new CaptureRegistry();
        registry.register(capture(DbOperation.UPDATE, "Person",
                new ListenerFilter("status", ValueType.STRING, "APPROVED"), null));
        registry.register(capture(DbOperation.UPDATE, "Person",
                new ListenerFilter("status", ValueType.STRING, "REJECTED"), null));

        CapturePublicationFilter filter = new CapturePublicationFilter(registry, objectMapper);

        assertThat(filter.allows(event("Person", "{\"status\":\"REJECTED\"}"), DbOperation.UPDATE)).isTrue();
        assertThat(filter.allows(event("Person", "{\"status\":\"DRAFT\"}"), DbOperation.UPDATE)).isFalse();
    }

    private ListenerDefinition capture(
            DbOperation operation,
            String entityName,
            ListenerFilter declarativeFilter,
            TentacolousFilter<?> customFilter
    ) {
        return new ListenerDefinition(null, null, operation, Person.class, entityName, "person", "id",
                declarativeFilter, customFilter, 0, new String[0]);
    }

    private DbChangeEvent event(String entityName, String payload) {
        DbChangeEvent event = new DbChangeEvent();
        event.setId(1L);
        event.setEntityName(entityName);
        event.setOperation("UPDATE");
        event.setPayload(payload);
        return event;
    }

    static class Person {
        public Long id;
        public String status;
    }
}
