package com.flowmint.events;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "raw_events")
public class RawEvent {
    @Id private UUID id;
    @Column(name = "external_event_id", nullable = false, unique = true) private String externalEventId;
    @Column(nullable = false) private String source;
    private String sender;
    @Column(name = "package_name") private String packageName;
    private String title;
    @Column(nullable = false, columnDefinition = "text") private String body;
    @Column(name = "event_timestamp", nullable = false) private Instant eventTimestamp;
    @Column(name = "device_id", nullable = false) private String deviceId;
    @Column(name = "received_at", nullable = false) private Instant receivedAt;
    @Enumerated(EnumType.STRING) @Column(name = "processing_status", nullable = false) private ProcessingStatus processingStatus;
    @Column(name = "processing_attempts", nullable = false) private int processingAttempts;
    @Column(name = "last_processing_error") private String lastProcessingError;

    protected RawEvent() {}
    public RawEvent(UUID id, String externalEventId, String source, String sender, String packageName, String title, String body, Instant eventTimestamp, String deviceId) {
        this.id = id; this.externalEventId = externalEventId; this.source = source; this.sender = sender; this.packageName = packageName; this.title = title; this.body = body; this.eventTimestamp = eventTimestamp; this.deviceId = deviceId; this.receivedAt = Instant.now(); this.processingStatus = ProcessingStatus.RECEIVED;
    }
    public UUID getId() { return id; }
    public String getExternalEventId() { return externalEventId; }
    public String getSource() { return source; }
    public String getSender() { return sender; }
    public String getPackageName() { return packageName; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Instant getEventTimestamp() { return eventTimestamp; }
    public ProcessingStatus getProcessingStatus() { return processingStatus; }
    public void markProcessing() { processingStatus = ProcessingStatus.PROCESSING; processingAttempts++; }
    public void markProcessed() { processingStatus = ProcessingStatus.PROCESSED; lastProcessingError = null; }
    public void markFailed(Exception exception) { processingStatus = ProcessingStatus.FAILED; lastProcessingError = exception.getClass().getSimpleName(); }
}
