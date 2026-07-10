package io.github.aimtone.tentacolous.registry;

import io.github.aimtone.tentacolous.model.DbOperation;
import io.github.aimtone.tentacolous.filter.TentacolousFilter;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ListenerDefinition {

    private final Object bean;
    private final Method method;
    private final DbOperation operation;
    private final Class<?> entityClass;
    private final String entityName;
    private final String tableName;
    private final String recordKeyField;
    private final ListenerFilter filter;
    private final TentacolousFilter<?> customFilter;
    private final int order;
    private final List<String> excludedColumns;

    public ListenerDefinition(
            Object bean,
            Method method,
            DbOperation operation,
            Class<?> entityClass,
            String entityName,
            String tableName,
            String recordKeyField,
            ListenerFilter filter,
            String[] excludedColumns
    ) {
        this(bean, method, operation, entityClass, entityName, tableName, recordKeyField, filter, null, 0, excludedColumns);
    }

    public ListenerDefinition(
            Object bean,
            Method method,
            DbOperation operation,
            Class<?> entityClass,
            String entityName,
            String tableName,
            String recordKeyField,
            ListenerFilter filter,
            TentacolousFilter<?> customFilter,
            String[] excludedColumns
    ) {
        this(bean, method, operation, entityClass, entityName, tableName, recordKeyField, filter, customFilter, 0, excludedColumns);
    }

    public ListenerDefinition(
            Object bean,
            Method method,
            DbOperation operation,
            Class<?> entityClass,
            String entityName,
            String tableName,
            String recordKeyField,
            ListenerFilter filter,
            TentacolousFilter<?> customFilter,
            int order,
            String[] excludedColumns
    ) {
        this.bean = bean;
        this.method = method;
        this.operation = operation;
        this.entityClass = entityClass;
        this.entityName = entityName;
        this.tableName = tableName;
        this.recordKeyField = recordKeyField;
        this.filter = filter;
        this.customFilter = customFilter;
        this.order = order;
        this.excludedColumns = excludedColumns == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(Arrays.asList(excludedColumns));
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

    public String getRecordKeyField() {
        return recordKeyField;
    }

    public ListenerFilter getFilter() {
        return filter;
    }

    public TentacolousFilter<?> getCustomFilter() {
        return customFilter;
    }

    public boolean hasCustomFilter() {
        return customFilter != null;
    }

    public int getOrder() {
        return order;
    }

    public List<String> getExcludedColumns() {
        return excludedColumns;
    }
}
