package io.github.aimtone.tentacolous.capture;

import io.github.aimtone.tentacolous.annotations.ValueType;
import io.github.aimtone.tentacolous.filter.TentacolousFilter;
import io.github.aimtone.tentacolous.filter.TentacolousFilterContext;
import io.github.aimtone.tentacolous.model.DbOperation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaptureTest {

    @Test
    void capturesAllOperationsByDefault() {
        Capture capture = Capture.of(Person.class);

        assertThat(capture.getOperations())
                .containsExactlyInAnyOrder(DbOperation.INSERT, DbOperation.UPDATE, DbOperation.DELETE);
        assertThat(capture.getValueType()).isEqualTo(ValueType.NONE);
        assertThat(capture.getFilter()).isNull();
    }

    @Test
    void keepsOnlyTheRequestedOperationsAndFilters() {
        Capture capture = Capture.of(Order.class)
                .operations(DbOperation.INSERT, DbOperation.UPDATE)
                .entityName("Order")
                .exclude("a", "b")
                .where("status", ValueType.STRING, "APPROVED")
                .order(5);

        assertThat(capture.getOperations()).containsExactlyInAnyOrder(DbOperation.INSERT, DbOperation.UPDATE);
        assertThat(capture.getEntityName()).isEqualTo("Order");
        assertThat(capture.getExcludedColumns()).containsExactly("a", "b");
        assertThat(capture.getField()).isEqualTo("status");
        assertThat(capture.getValue()).isEqualTo("APPROVED");
        assertThat(capture.getOrder()).isEqualTo(5);
    }

    @Test
    void acceptsProgrammaticFilter() {
        TentacolousFilter<Object> filter = new TentacolousFilter<>() {
            @Override
            public boolean accept(TentacolousFilterContext<Object> context) {
                return true;
            }
        };

        assertThat(Capture.of(Person.class).filter(filter).getFilter()).isSameAs(filter);
    }

    @Test
    void rejectsNullEntity() {
        assertThatThrownBy(() -> Capture.of(null)).isInstanceOf(IllegalArgumentException.class);
    }

    static class Person {
        private Long id;
    }

    static class Order {
        private Long id;
    }
}
