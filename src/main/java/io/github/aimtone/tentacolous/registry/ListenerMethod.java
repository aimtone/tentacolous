package io.github.aimtone.tentacolous.registry;

import io.github.aimtone.tentacolous.annotations.ValueType;
import io.github.aimtone.tentacolous.model.DbOperation;

import java.lang.reflect.Method;

public class ListenerMethod {

    private final Object bean;
    private final Method method;
    private final DbOperation operation;
    private final Class<?> entityClass;
    private final String entityName;
    private final String tableName;
    private final String fieldName;
    private final ValueType valueType;
    private final String stringValue;

    public ListenerMethod(
            Object bean,
            Method method,
            DbOperation operation,
            Class<?> entityClass,
            String entityName,
            String tableName,
            String fieldName,
            ValueType valueType,
            String stringValue
    ) {
        this.bean = bean;
        this.method = method;
        this.operation = operation;
        this.entityClass = entityClass;
        this.entityName = entityName;
        this.tableName = tableName;
        this.fieldName = fieldName;
        this.valueType = valueType;
        this.stringValue = stringValue;
    }

    public Object getBean() {
        return bean;
    }

    public Method getMethod() {
        return method;
    }

    public DbOperation getOperation() {
        return operation;
    }

    public Class<?> getEntityClass() {
        return entityClass;
    }

    public String getEntityName() {
        return entityName;
    }

    public String getTableName() {
        return tableName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public ValueType getValueType() {
        return valueType;
    }

    public String getStringValue() {
        return stringValue;
    }

    public boolean hasValueFilter() {
        return valueType != ValueType.NONE;
    }
}
