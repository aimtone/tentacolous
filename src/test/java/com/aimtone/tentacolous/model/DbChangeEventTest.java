package com.aimtone.tentacolous.model;

import com.aimtone.tentacolous.config.DbListenerProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DbChangeEventTest {

    @Test
    void storesDatabaseChangeEventData() {
        DbChangeEvent event = new DbChangeEvent();

        event.setId(1L);
        event.setEntityName("User");
        event.setOperation("UPDATE");
        event.setPayload("{\"id\":1}");

        assertThat(event.getId()).isEqualTo(1L);
        assertThat(event.getEntityName()).isEqualTo("User");
        assertThat(event.getOperation()).isEqualTo("UPDATE");
        assertThat(event.getPayload()).isEqualTo("{\"id\":1}");
    }

    @Test
    void propertiesExposeDefaultsAndConfiguredValues() {
        DbListenerProperties properties = new DbListenerProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getEventTable()).isEqualTo("db_change_event");
        assertThat(properties.getPollInterval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.getInitialDelay()).isEqualTo(Duration.ZERO);
        assertThat(properties.getBatchSize()).isEqualTo(100);
        assertThat(properties.getMaxAttempts()).isEqualTo(3);
        assertThat(properties.getSchemaManagement()).isEqualTo(DbListenerProperties.SchemaManagement.AUTO);

        properties.setEnabled(false);
        properties.setEventTable("audit_events");
        properties.setPollInterval(Duration.ofSeconds(5));
        properties.setInitialDelay(Duration.ofSeconds(2));
        properties.setBatchSize(25);
        properties.setMaxAttempts(7);
        properties.setSchemaManagement(DbListenerProperties.SchemaManagement.NONE);

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getEventTable()).isEqualTo("audit_events");
        assertThat(properties.getPollInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getInitialDelay()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.getBatchSize()).isEqualTo(25);
        assertThat(properties.getMaxAttempts()).isEqualTo(7);
        assertThat(properties.getSchemaManagement()).isEqualTo(DbListenerProperties.SchemaManagement.NONE);
    }
}
