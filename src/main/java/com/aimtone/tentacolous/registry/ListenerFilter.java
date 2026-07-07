package com.aimtone.tentacolous.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.aimtone.tentacolous.annotations.ValueType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public class ListenerFilter {

    private final String fieldName;
    private final ValueType valueType;
    private final String stringValue;

    public ListenerFilter(
            String fieldName,
            ValueType valueType,
            String stringValue
    ) {
        this.fieldName = fieldName;
        this.valueType = valueType;
        this.stringValue = stringValue;
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

    public boolean isEnabled() {
        return valueType != ValueType.NONE;
    }

    public boolean matches(JsonNode root) {
        if (!isEnabled()) {
            return true;
        }

        JsonNode field = root.path(fieldName);

        if (field.isMissingNode() || field.isNull()) {
            return false;
        }

        if (valueType == ValueType.STRING) {
            return stringValue.equals(field.asText());
        }

        if (valueType == ValueType.BOOLEAN) {
            return expectedBoolean() == field.asBoolean();
        }

        if (valueType == ValueType.NUMBER) {
            return expectedLong() == field.asLong();
        }

        if (valueType == ValueType.INTEGER) {
            return Integer.parseInt(stringValue) == field.asInt();
        }

        if (valueType == ValueType.LONG) {
            return Long.parseLong(stringValue) == field.asLong();
        }

        if (valueType == ValueType.DECIMAL) {
            return decimal(stringValue).compareTo(decimal(field.asText())) == 0;
        }

        if (valueType == ValueType.DOUBLE) {
            return Double.compare(Double.parseDouble(stringValue), field.asDouble()) == 0;
        }

        if (valueType == ValueType.DATE) {
            return LocalDate.parse(stringValue).equals(LocalDate.parse(field.asText()));
        }

        if (valueType == ValueType.TIME) {
            return LocalTime.parse(stringValue).equals(LocalTime.parse(field.asText()));
        }

        if (valueType == ValueType.DATETIME) {
            return dateTimeEquals(stringValue, field.asText());
        }

        if (valueType == ValueType.UUID) {
            return UUID.fromString(stringValue).equals(UUID.fromString(field.asText()));
        }

        return true;
    }

    private boolean expectedBoolean() {
        return Boolean.parseBoolean(stringValue);
    }

    private long expectedLong() {
        return Long.parseLong(stringValue);
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private boolean dateTimeEquals(String expected, String actual) {
        try {
            return Instant.parse(expected).equals(Instant.parse(actual));
        } catch (Exception ignored) {
            return LocalDateTime.parse(expected).equals(LocalDateTime.parse(actual));
        }
    }
}
