package io.github.aimtone.tentacolous.scanner;

import io.github.aimtone.tentacolous.annotations.UponDeleting;
import io.github.aimtone.tentacolous.annotations.UponInserting;
import io.github.aimtone.tentacolous.annotations.UponUpdating;
import io.github.aimtone.tentacolous.annotations.ValueType;
import io.github.aimtone.tentacolous.model.DbOperation;
import io.github.aimtone.tentacolous.registry.ListenerDefinition;
import io.github.aimtone.tentacolous.registry.ListenerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DbListenerMethodScannerTest {

    @Test
    void registersInsertUpdateAndDeleteListeners() {
        ListenerRegistry registry = new ListenerRegistry();
        DbListenerMethodScanner scanner = new DbListenerMethodScanner(registry);

        scanner.postProcessAfterInitialization(new UserListeners(), "userListeners");

        assertThat(registry.getListeners(DbOperation.INSERT, "User")).hasSize(1);
        assertThat(registry.getListeners(DbOperation.UPDATE, "User")).hasSize(1);
        assertThat(registry.getListeners(DbOperation.DELETE, "User")).hasSize(1);

        List<ListenerDefinition> updateListeners = registry.getListeners(DbOperation.UPDATE, "User");
        ListenerDefinition updateListener = updateListeners.get(0);

        assertThat(updateListener.getTableName()).isEqualTo("user");
        assertThat(updateListener.getFilter().getFieldName()).isEqualTo("status");
        assertThat(updateListener.getFilter().getValueType()).isEqualTo(ValueType.STRING);
        assertThat(updateListener.getFilter().getStringValue()).isEqualTo("APROBADO");
    }

    @Test
    void registersListenersBySimpleNameWhenEntityNameIsMissing() {
        ListenerRegistry registry = new ListenerRegistry();
        DbListenerMethodScanner scanner = new DbListenerMethodScanner(registry);

        scanner.postProcessAfterInitialization(new SimpleNameListener(), "simpleNameListener");

        assertThat(registry.getListeners(DbOperation.INSERT, "User")).hasSize(1);
    }

    @Test
    void infersTableNameWhenAnnotationDoesNotDefineTable() {
        ListenerRegistry registry = new ListenerRegistry();
        DbListenerMethodScanner scanner = new DbListenerMethodScanner(registry);

        scanner.postProcessAfterInitialization(new InferredTableListener(), "inferredTableListener");

        ListenerDefinition listener = registry.getListeners(DbOperation.INSERT, "UserAccount").get(0);

        assertThat(listener.getTableName()).isEqualTo("user_account");
    }

    @Test
    void registersAnnotatedMethodsFromCglibProxiedBeans() throws Exception {
        ListenerRegistry registry = new ListenerRegistry();
        DbListenerMethodScanner scanner = new DbListenerMethodScanner(registry);
        ProxiedListener target = new ProxiedListener();
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);

        scanner.postProcessAfterInitialization(proxyFactory.getProxy(), "proxiedListener");

        List<ListenerDefinition> listeners = registry.getListeners(DbOperation.INSERT, "User");

        assertThat(listeners).hasSize(1);
        listeners.get(0).getMethod().invoke(listeners.get(0).getBean(), new User());
        assertThat(target.invocations).isEqualTo(1);
    }

    @Test
    void rejectsValueFilterWithoutField() {
        ListenerRegistry registry = new ListenerRegistry();
        DbListenerMethodScanner scanner = new DbListenerMethodScanner(registry);

        assertThatThrownBy(() -> scanner.postProcessAfterInitialization(new InvalidFilterListener(), "invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must define field");
    }

    static class UserListeners {

        @UponInserting(entity = User.class, entityName = "User")
        public void onInserting(User user) {
        }

        @UponUpdating(
                entity = User.class,
                entityName = "User",
                field = "status",
                valueType = ValueType.STRING,
                value = "APROBADO"
        )
        public void onUpdating(User user) {
        }

        @UponDeleting(entity = User.class, entityName = "User")
        public void onDeleting(User user) {
        }
    }

    static class SimpleNameListener {

        @UponInserting(entity = User.class)
        public void onInserting(User user) {
        }
    }

    static class InferredTableListener {

        @UponInserting(entity = UserAccount.class)
        public void onInserting(UserAccount userAccount) {
        }
    }

    static class InvalidFilterListener {

        @UponInserting(entity = User.class, valueType = ValueType.BOOLEAN, value = "true")
        public void onInserting(User user) {
        }
    }

    static class ProxiedListener {

        int invocations;

        @UponInserting(entity = User.class)
        public void onInserting(User user) {
            invocations++;
        }
    }

    static class User {
    }

    static class UserAccount {
    }
}
