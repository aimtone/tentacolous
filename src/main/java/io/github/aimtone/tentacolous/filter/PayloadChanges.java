package io.github.aimtone.tentacolous.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Computes which JSON fields differ between the current and previous payloads of an update event. */
public final class PayloadChanges {

    private PayloadChanges() {
    }

    public static Set<String> changedFields(ObjectMapper objectMapper, String currentPayload, String oldPayload) {
        if (currentPayload == null || oldPayload == null) {
            return Collections.emptySet();
        }

        try {
            JsonNode current = objectMapper.readTree(currentPayload);
            JsonNode previous = objectMapper.readTree(oldPayload);
            Set<String> fieldNames = new LinkedHashSet<>();
            current.fieldNames().forEachRemaining(fieldNames::add);
            previous.fieldNames().forEachRemaining(fieldNames::add);
            fieldNames.removeIf(fieldName -> Objects.equals(current.get(fieldName), previous.get(fieldName)));
            return fieldNames;
        } catch (Exception e) {
            throw new RuntimeException("Error comparing current and previous event payloads", e);
        }
    }
}
