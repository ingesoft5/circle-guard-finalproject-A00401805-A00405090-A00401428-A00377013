package com.circleguard.form.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "form_event_outbox")
public class FormOutboxEvent {
    @Id private UUID id;
    private String topic;
    @Column(name = "event_key") private String eventKey;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb") private Map<String, Object> payload;
    private String traceparent;
    private int attempts;
    @Column(name = "next_attempt_at") private OffsetDateTime nextAttemptAt;
    @Column(name = "created_at") private OffsetDateTime createdAt;

    protected FormOutboxEvent() { }
    public FormOutboxEvent(String topic, String eventKey, Map<String, Object> payload, String traceparent) {
        this.id = UUID.randomUUID();
        this.topic = topic;
        this.eventKey = eventKey;
        this.payload = payload;
        this.traceparent = traceparent;
        this.nextAttemptAt = OffsetDateTime.now();
        this.createdAt = this.nextAttemptAt;
    }
    public UUID getId() { return id; }
    public String getTopic() { return topic; }
    public String getEventKey() { return eventKey; }
    public Map<String, Object> getPayload() { return payload; }
    public String getTraceparent() { return traceparent; }
    public int getAttempts() { return attempts; }
    public void defer(long delaySeconds) {
        attempts++;
        nextAttemptAt = OffsetDateTime.now().plusSeconds(delaySeconds);
    }
}
