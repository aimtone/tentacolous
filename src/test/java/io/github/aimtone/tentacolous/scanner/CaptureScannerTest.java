package io.github.aimtone.tentacolous.scanner;

import io.github.aimtone.tentacolous.annotations.ActionListener;
import io.github.aimtone.tentacolous.annotations.TentacolousCapture;
import io.github.aimtone.tentacolous.annotations.ValueType;
import io.github.aimtone.tentacolous.model.DbOperation;
import io.github.aimtone.tentacolous.registry.CaptureRegistry;
import io.github.aimtone.tentacolous.registry.ListenerDefinition;
import io.github.aimtone.tentacolous.registry.ListenerRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CaptureScannerTest {

    @Test
    void registersAllOperationsWhenActionsAreNotSpecified() {
        CaptureRegistry captureRegistry = new CaptureRegistry();
        DbListenerMethodScanner scanner = new DbListenerMethodScanner(new ListenerRegistry(), captureRegistry);

        scanner.postProcessAfterInitialization(new CaptureAll(), "captureAll");

        assertThat(captureRegistry.getAll()).extracting(ListenerDefinition::getOperation)
                .containsExactlyInAnyOrder(DbOperation.INSERT, DbOperation.UPDATE, DbOperation.DELETE);
        assertThat(captureRegistry.getAll()).allSatisfy(definition -> {
            assertThat(definition.getEntityName()).isEqualTo("Person");
            assertThat(definition.getTableName()).isEqualTo("person");
        });
    }

    @Test
    void registersOnlyTheRequestedActions() {
        CaptureRegistry captureRegistry = new CaptureRegistry();
        DbListenerMethodScanner scanner = new DbListenerMethodScanner(new ListenerRegistry(), captureRegistry);

        scanner.postProcessAfterInitialization(new CaptureInserts(), "captureInserts");

        assertThat(captureRegistry.getAll()).extracting(ListenerDefinition::getOperation)
                .containsExactly(DbOperation.INSERT);
    }

    @Test
    void carriesTheDeclarativeFilterAndOrderFromTheAnnotation() {
        CaptureRegistry captureRegistry = new CaptureRegistry();
        DbListenerMethodScanner scanner = new DbListenerMethodScanner(new ListenerRegistry(), captureRegistry);

        scanner.postProcessAfterInitialization(new CaptureApproved(), "captureApproved");

        ListenerDefinition definition = captureRegistry.getMatching(DbOperation.UPDATE, "Person").get(0);
        assertThat(definition.getFilter().getFieldName()).isEqualTo("status");
        assertThat(definition.getFilter().getValueType()).isEqualTo(ValueType.STRING);
        assertThat(definition.getFilter().getStringValue()).isEqualTo("APPROVED");
        assertThat(definition.getOrder()).isEqualTo(7);
    }

    @Test
    void ignoresCapturesWhenNoCaptureRegistryIsAvailable() {
        DbListenerMethodScanner scanner = new DbListenerMethodScanner(new ListenerRegistry());

        scanner.postProcessAfterInitialization(new CaptureAll(), "captureAll");
        // no exception, nothing to assert beyond a clean run
    }

    @TentacolousCapture(entity = Person.class)
    static class CaptureAll {
    }

    @TentacolousCapture(entity = Person.class, actions = ActionListener.INSERT)
    static class CaptureInserts {
    }

    @TentacolousCapture(
            entity = Person.class,
            actions = ActionListener.UPDATE,
            field = "status",
            valueType = ValueType.STRING,
            value = "APPROVED",
            order = 7)
    static class CaptureApproved {
    }

    static class Person {
        private Long id;
        private String name;
    }
}
