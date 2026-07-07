package io.github.aimtone.tentacolous.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "tentacolous")
public class DbListenerProperties {

    private boolean enabled = true;
    private String eventTable = "db_change_event";
    private Duration pollInterval = Duration.ofSeconds(1);
    private Duration initialDelay = Duration.ZERO;
    private int batchSize = 100;
    private int maxAttempts = 3;
    private SchemaManagement schemaManagement = SchemaManagement.AUTO;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEventTable() {
        return eventTable;
    }

    public void setEventTable(String eventTable) {
        this.eventTable = eventTable;
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    public Duration getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(Duration initialDelay) {
        this.initialDelay = initialDelay;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public SchemaManagement getSchemaManagement() {
        return schemaManagement;
    }

    public void setSchemaManagement(SchemaManagement schemaManagement) {
        this.schemaManagement = schemaManagement;
    }

    public enum SchemaManagement {
        CREATE,
        VALIDATE,
        AUTO,
        NONE
    }
}
