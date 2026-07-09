package io.github.aimtone.tentacolous.model;

public class DbChangeEvent {

    private Long id;
    private String entityName;
    private String operation;
    private String payload;
    private String oldPayload;
    private String recordKey;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getOldPayload() {
        return oldPayload;
    }

    public void setOldPayload(String oldPayload) {
        this.oldPayload = oldPayload;
    }

    public String getRecordKey() {
        return recordKey;
    }

    public void setRecordKey(String recordKey) {
        this.recordKey = recordKey;
    }
}
