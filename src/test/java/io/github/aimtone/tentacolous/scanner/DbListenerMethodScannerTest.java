package io.github.aimtone.tentacolous.scanner;

import io.github.aimtone.tentacolous.annotations.UponDeleting;
import io.github.aimtone.tentacolous.annotations.UponInserting;
import io.github.aimtone.tentacolous.annotations.UponUpdating;
import io.github.aimtone.tentacolous.annotations.ActionListener;
import io.github.aimtone.tentacolous.annotations.TentacolousListener;
import io.github.aimtone.tentacolous.annotations.ValueType;
import io.github.aimtone.tentacolous.model.DbOperation;
import io.github.aimtone.tentacolous.filter.TentacolousFilter;
import io.github.aimtone.tentacolous.filter.TentacolousFilterContext;
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
    void registersGenericListenersForEveryAction() {
        ListenerRegistry registry = new ListenerRegistry();
        DbListenerMethodScanner scanner = new DbListenerMethodScanner(registry);

        scanner.postProcessAfterInitialization(new GenericListeners(), "genericListeners");

        assertThat(registry.getListeners(DbOperation.INSERT, "User")).hasSize(1);
        assertThat(registry.getListeners(DbOperation.UPDATE, "User")).hasSize(1);
        assertThat(registry.getListeners(DbOperation.DELETE, "User")).hasSize(1);
        assertThat(registry.getListeners(DbOperation.UPDATE, "User").get(0).getFilter().getFieldName())
                .isEqualTo("status");
    }

    @Test
    void appliesActionSpecificParameterRulesToGenericListeners() {
        ListenerRegistry registry = new ListenerRegistry();
        DbListenerMethodScanner scanner = new DbListenerMethodScanner(registry);

        assertThatThrownBy(() -> scanner.postProcessAfterInitialization(new InvalidGenericInsert(), "invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one parameter");
    }

    @Test
    void rejectsCustomFilterWithIncompatibleEntityType() {
        ListenerRegistry registry = new ListenerRegistry();
        DbListenerMethodScanner scanner = new DbListenerMethodScanner(registry);

        assertThatThrownBy(() -> scanner.postProcessAfterInitialization(new IncompatibleFilterListener(), "invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expects entity")
                .hasMessageContaining("listener entity");
    }

    @Test
    void rejectsMultipleListenerAnnotationsForTheSameOperation() {
        ListenerRegistry registry = new ListenerRegistry();
        DbListenerMethodScanner scanner = new DbListenerMethodScanner(registry);

        assertThatThrownBy(() -> scanner.postProcessAfterInitialization(new DuplicateAnnotationListener(), "invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than one Tentacolous listener annotation for UPDATE");
    }

    @Test
    void acceptsMultipleListenerAnnotationsForDifferentOperationsOnTheSameMethod() {
        ListenerRegistry registry = new ListenerRegistry();
        DbListenerMethodScanner scanner = new DbListenerMethodScanner(registry);

        scanner.postProcessAfterInitialization(new MultiOperationMethodListener(), "multiOperationMethodListener");

        assertThat(registry.getListeners(DbOperation.INSERT, "User")).hasSize(1);
        assertThat(registry.getListeners(DbOperation.UPDATE, "User")).hasSize(1);
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
                .hasMessageContaining("field, valueType and value must be defined together")
                .hasMessageContaining("field = \"name\", valueType = ValueType.STRING, value = \"Anthony\"");
    }

    @Test
    void rejectsValueFilterWithoutValueType() {
        ListenerRegistry registry = new ListenerRegistry();
        DbListenerMethodScanner scanner = new DbListenerMethodScanner(registry);

        assertThatThrownBy(() -> scanner.postProcessAfterInitialization(new InvalidFilterWithoutValueTypeListener(), "invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field, valueType and value must be defined together");
    }

    @Test
    void acceptsTwoParametersForUpdateListeners() {
        ListenerRegistry registry = new ListenerRegistry();
        DbListenerMethodScanner scanner = new DbListenerMethodScanner(registry);

        scanner.postProcessAfterInitialization(new UpdateWithOldEntityListener(), "updateWithOldEntityListener");

        assertThat(registry.getListeners(DbOperation.UPDATE, "User")).hasSize(1);
    }

    @Test
    void acceptsThreeParametersForUpdateListenersWhenHistoryIsListOrArray() {
        ListenerRegistry registry = new ListenerRegistry();
        DbListenerMethodScanner scanner = new DbListenerMethodScanner(registry);

        scanner.postProcessAfterInitialization(new UpdateWithHistoryListener(), "updateWithHistoryListener");

        assertThat(registry.getListeners(DbOperation.UPDATE, "User")).hasSize(2);
    }

    @Test
    void acceptsTwoParametersForDeleteListenersWhenHistoryIsListOrArray() {
        ListenerRegistry registry = new ListenerRegistry();
        DbListenerMethodScanner scanner = new DbListenerMethodScanner(registry);

        scanner.postProcessAfterInitialization(new DeleteWithHistoryListener(), "deleteWithHistoryListener");

        assertThat(registry.getListeners(DbOperation.DELETE, "User")).hasSize(2);
    }

    @Test
    void rejectsTwoParametersForInsertListeners() {
        ListenerRegistry registry = new ListenerRegistry();
        DbListenerMethodScanner scanner = new DbListenerMethodScanner(registry);

        assertThatThrownBy(() -> scanner.postProcessAfterInitialization(new InsertWithTwoParametersListener(), "invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one parameter");
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

    static class GenericListeners {

        @TentacolousListener(entity = User.class, action = ActionListener.INSERT)
        public void onInsert(User user) {
        }

        @TentacolousListener(
                entity = User.class,
                action = ActionListener.UPDATE,
                field = "status",
                valueType = ValueType.STRING,
                value = "APROBADO"
        )
        public void onUpdate(User newUser, User oldUser, List<User> history) {
        }

        @TentacolousListener(entity = User.class, action = ActionListener.DELETE)
        public void onDelete(User user, User[] history) {
        }
    }

    static class InvalidGenericInsert {

        @TentacolousListener(entity = User.class, action = ActionListener.INSERT)
        public void onInsert(User user, User otherUser) {
        }
    }

    static class IncompatibleFilterListener {

        @UponInserting(entity = User.class, filter = UserAccountFilter.class)
        public void onInsert(User user) {
        }
    }

    static class DuplicateAnnotationListener {

        @UponUpdating(entity = User.class)
        @TentacolousListener(entity = User.class, action = ActionListener.UPDATE)
        public void onUpdate(User user) {
        }
    }

    static class MultiOperationMethodListener {

        @UponInserting(entity = User.class)
        @UponUpdating(entity = User.class)
        public void onInsertOrUpdate(User user) {
        }
    }

    static class UserAccountFilter extends TentacolousFilter<UserAccount> {

        @Override
        public boolean accept(TentacolousFilterContext<UserAccount> context) {
            return true;
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

    static class InvalidFilterWithoutValueTypeListener {

        @UponUpdating(entity = User.class, field = "name", value = "Anthony")
        public void onUpdating(User user) {
        }
    }

    static class UpdateWithOldEntityListener {

        @UponUpdating(entity = User.class)
        public void onUpdating(User newUser, User oldUser) {
        }
    }

    static class UpdateWithHistoryListener {

        @UponUpdating(entity = User.class)
        public void onUpdatingWithList(User newUser, User oldUser, List<User> history) {
        }

        @UponUpdating(entity = User.class)
        public void onUpdatingWithArray(User newUser, User oldUser, User[] history) {
        }
    }

    static class DeleteWithHistoryListener {

        @UponDeleting(entity = User.class)
        public void onDeletingWithList(User deletedUser, List<User> history) {
        }

        @UponDeleting(entity = User.class)
        public void onDeletingWithArray(User deletedUser, User[] history) {
        }
    }

    static class InsertWithTwoParametersListener {

        @UponInserting(entity = User.class)
        public void onInserting(User user, User otherUser) {
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
