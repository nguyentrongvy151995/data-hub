package com.anonymous.datahub.data_hub.infrastructure.persistence.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "raw_event")
@CompoundIndexes({
        @CompoundIndex(
                name = "idx_event_type_source_status_created_at",
                def = "{'eventType': 1, 'sourceSystem': 1, 'status': 1, 'createdAt': -1}"
        ),
        @CompoundIndex(
                name = "idx_event_source_status_created_at",
                def = "{'sourceSystem': 1, 'status': 1, 'createdAt': -1}"
        ),
        @CompoundIndex(
                name = "idx_event_status_created_at",
                def = "{'status': 1, 'createdAt': -1}"
        )
})
public class RawEventDocument {

    @Id
    private String id;

    @Indexed(name = "uk_event_event_id", unique = true)
    private String eventId;

    private String eventType;

    private String sourceSystem;

    private String status;

    private String payload;

    @Indexed(name = "idx_event_created_at")
    private Instant createdAt;

    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
