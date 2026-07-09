package io.github.aimtone.tentacolous.scanner;

import io.github.aimtone.tentacolous.annotations.UponDeleting;
import io.github.aimtone.tentacolous.annotations.UponInserting;
import io.github.aimtone.tentacolous.annotations.UponUpdating;
import io.github.aimtone.tentacolous.annotations.ValueType;
import io.github.aimtone.tentacolous.model.DbOperation;
import io.github.aimtone.tentacolous.registry.ListenerDefinition;
import io.github.aimtone.tentacolous.registry.ListenerFilter;
import io.github.aimtone.tentacolous.registry.ListenerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class DbListenerMethodScanner implements SmartInitializingSingleton, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(DbListenerMethodScanner.class);

    private enum ParameterMode {
        SINGLE,
        UPDATE,
        DELETE
    }

    private final ListenerRegistry listenerRegistry;
    private ApplicationContext applicationContext;
    private boolean scanned;
    private int registeredListeners;

    public DbListenerMethodScanner(ListenerRegistry listenerRegistry) {
        this.listenerRegistry = listenerRegistry;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (applicationContext == null || scanned) {
            return;
        }

        scanned = true;
        Map<String, Object> beans = applicationContext.getBeansOfType(Object.class);

        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            postProcessAfterInitialization(entry.getValue(), entry.getKey());
        }

        log.info("Tentacolous registered {} listener method(s)", registeredListeners);
    }

    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        Method[] methods = ReflectionUtils.getUniqueDeclaredMethods(targetClass);

        for (Method method : methods) {
            registerUponInserting(bean, method);
            registerUponUpdating(bean, method);
            registerUponDeleting(bean, method);
        }

        return bean;
    }

    private void registerUponInserting(Object bean, Method annotatedMethod) {
        UponInserting annotation = annotatedMethod.getAnnotation(UponInserting.class);

        if (annotation == null) {
            return;
        }

        validateMethod(annotatedMethod, annotation.entity(), annotation.valueType(), annotation.field(), annotation.value(), "@UponInserting", ParameterMode.SINGLE);
        Method invocableMethod = AopUtils.selectInvocableMethod(annotatedMethod, bean.getClass());

        listenerRegistry.registerListener(new ListenerDefinition(
                bean,
                invocableMethod,
                DbOperation.INSERT,
                annotation.entity(),
                resolveEntityName(annotation.entity(), annotation.entityName()),
                resolveTableName(annotation.entity()),
                resolveRecordKeyField(annotation.entity()),
                filter(annotation.valueType(), annotation.field(), annotation.value()),
                annotation.exclude()
        ));
        registeredListeners++;
    }

    private void registerUponUpdating(Object bean, Method annotatedMethod) {
        UponUpdating annotation = annotatedMethod.getAnnotation(UponUpdating.class);

        if (annotation == null) {
            return;
        }

        validateMethod(annotatedMethod, annotation.entity(), annotation.valueType(), annotation.field(), annotation.value(), "@UponUpdating", ParameterMode.UPDATE);
        Method invocableMethod = AopUtils.selectInvocableMethod(annotatedMethod, bean.getClass());

        listenerRegistry.registerListener(new ListenerDefinition(
                bean,
                invocableMethod,
                DbOperation.UPDATE,
                annotation.entity(),
                resolveEntityName(annotation.entity(), annotation.entityName()),
                resolveTableName(annotation.entity()),
                resolveRecordKeyField(annotation.entity()),
                filter(annotation.valueType(), annotation.field(), annotation.value()),
                annotation.exclude()
        ));
        registeredListeners++;
    }

    private void registerUponDeleting(Object bean, Method annotatedMethod) {
        UponDeleting annotation = annotatedMethod.getAnnotation(UponDeleting.class);

        if (annotation == null) {
            return;
        }

        validateMethod(annotatedMethod, annotation.entity(), annotation.valueType(), annotation.field(), annotation.value(), "@UponDeleting", ParameterMode.DELETE);
        Method invocableMethod = AopUtils.selectInvocableMethod(annotatedMethod, bean.getClass());

        listenerRegistry.registerListener(new ListenerDefinition(
                bean,
                invocableMethod,
                DbOperation.DELETE,
                annotation.entity(),
                resolveEntityName(annotation.entity(), annotation.entityName()),
                resolveTableName(annotation.entity()),
                resolveRecordKeyField(annotation.entity()),
                filter(annotation.valueType(), annotation.field(), annotation.value()),
                annotation.exclude()
        ));
        registeredListeners++;
    }

    private ListenerFilter filter(
            ValueType valueType,
            String field,
            String value
    ) {
        return new ListenerFilter(field, valueType, value);
    }

    private String resolveEntityName(Class<?> entityClass, String entityName) {
        if (entityName != null && !entityName.isBlank()) {
            return entityName;
        }

        return entityClass.getSimpleName();
    }

    private String resolveTableName(Class<?> entityClass) {
        Annotation tableAnnotation = findAnnotation(entityClass, "jakarta.persistence.Table", "javax.persistence.Table");

        if (tableAnnotation != null) {
            String name = annotationStringValue(tableAnnotation, "name");
            String schema = annotationStringValue(tableAnnotation, "schema");

            if (name != null && !name.isBlank()) {
                if (schema != null && !schema.isBlank()) {
                    return schema + "." + name;
                }

                return name;
            }
        }

        return toSnakeCase(entityClass.getSimpleName());
    }

    private String resolveRecordKeyField(Class<?> entityClass) {
        for (Field field : entityClass.getDeclaredFields()) {
            if (findAnnotation(field, "jakarta.persistence.Id", "javax.persistence.Id") != null) {
                String columnName = resolveColumnName(field);

                if (columnName != null && !columnName.isBlank()) {
                    return columnName;
                }

                return field.getName();
            }
        }

        return "id";
    }

    private String resolveColumnName(Field field) {
        Annotation columnAnnotation = findAnnotation(field, "jakarta.persistence.Column", "javax.persistence.Column");

        if (columnAnnotation == null) {
            return "";
        }

        return annotationStringValue(columnAnnotation, "name");
    }

    private Annotation findAnnotation(Class<?> type, String... annotationTypeNames) {
        for (Annotation annotation : type.getAnnotations()) {
            String annotationTypeName = annotation.annotationType().getName();

            for (String expectedTypeName : annotationTypeNames) {
                if (expectedTypeName.equals(annotationTypeName)) {
                    return annotation;
                }
            }
        }

        return null;
    }

    private Annotation findAnnotation(Field field, String... annotationTypeNames) {
        for (Annotation annotation : field.getAnnotations()) {
            String annotationTypeName = annotation.annotationType().getName();

            for (String expectedTypeName : annotationTypeNames) {
                if (expectedTypeName.equals(annotationTypeName)) {
                    return annotation;
                }
            }
        }

        return null;
    }

    private String annotationStringValue(Annotation annotation, String methodName) {
        try {
            Object value = annotation.annotationType().getMethod(methodName).invoke(annotation);
            return value instanceof String stringValue ? stringValue : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String toSnakeCase(String value) {
        return value
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .toLowerCase();
    }

    private void validateMethod(
            Method method,
            Class<?> entityClass,
            ValueType valueType,
            String field,
            String value,
            String annotationName,
            ParameterMode parameterMode
    ) {
        int parameterCount = method.getParameterCount();
        int maxParameterCount = switch (parameterMode) {
            case SINGLE -> 1;
            case UPDATE -> 3;
            case DELETE -> 2;
        };

        if (parameterCount < 1 || parameterCount > maxParameterCount) {
            throw new IllegalArgumentException(
                    annotationName + " method must have " + parameterDescription(parameterMode)
                            + ": " + method.getName()
            );
        }

        Class<?>[] parameterTypes = method.getParameterTypes();

        if (!parameterTypes[0].isAssignableFrom(entityClass)) {
            throw new IllegalArgumentException(
                    annotationName + " method parameter must be compatible with entity "
                            + entityClass.getName() + ": " + method.getName()
            );
        }

        if (parameterMode == ParameterMode.UPDATE && parameterCount == 2 && !parameterTypes[1].isAssignableFrom(entityClass)) {
            throw new IllegalArgumentException(
                    annotationName + " method old entity parameter must be compatible with entity "
                            + entityClass.getName() + ": " + method.getName()
            );
        }

        if (parameterMode == ParameterMode.UPDATE && parameterCount == 3) {
            if (!parameterTypes[1].isAssignableFrom(entityClass)) {
                throw new IllegalArgumentException(
                        annotationName + " method old entity parameter must be compatible with entity "
                                + entityClass.getName() + ": " + method.getName()
                );
            }

            validateHistoryParameter(method, parameterTypes[2], 2, entityClass, annotationName);
        }

        if (parameterMode == ParameterMode.DELETE && parameterCount == 2) {
            validateHistoryParameter(method, parameterTypes[1], 1, entityClass, annotationName);
        }

        if (valueType != ValueType.NONE && (field == null || field.isBlank())) {
            throw new IllegalArgumentException(
                    annotationName + " method with value filter must define field: " + method.getName()
            );
        }

        if (valueType != ValueType.NONE && (value == null || value.isBlank())) {
            throw new IllegalArgumentException(
                    annotationName + " method with value filter must define value: " + method.getName()
            );
        }
    }

    private String parameterDescription(ParameterMode parameterMode) {
        return switch (parameterMode) {
            case SINGLE -> "exactly one parameter";
            case UPDATE -> "one, two or three parameters";
            case DELETE -> "one or two parameters";
        };
    }

    private void validateHistoryParameter(
            Method method,
            Class<?> historyParameterType,
            int parameterIndex,
            Class<?> entityClass,
            String annotationName
    ) {
        if (historyParameterType.isArray()) {
            Class<?> componentType = historyParameterType.getComponentType();

            if (!componentType.isAssignableFrom(entityClass)) {
                throw new IllegalArgumentException(
                        annotationName + " method history array component must be compatible with entity "
                                + entityClass.getName() + ": " + method.getName()
                );
            }

            return;
        }

        if (!List.class.isAssignableFrom(historyParameterType)) {
            throw new IllegalArgumentException(
                    annotationName + " method history parameter must be a List or array: " + method.getName()
            );
        }

        Type genericParameterType = method.getGenericParameterTypes()[parameterIndex];

        if (genericParameterType instanceof ParameterizedType parameterizedType) {
            Type typeArgument = parameterizedType.getActualTypeArguments()[0];

            if (typeArgument instanceof Class<?> historyType && !historyType.isAssignableFrom(entityClass)) {
                throw new IllegalArgumentException(
                        annotationName + " method history List type must be compatible with entity "
                                + entityClass.getName() + ": " + method.getName()
                );
            }
        }
    }
}
