package com.flowmint.events;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "raw_events")
public class RawEvent {
    private static final int MAX_REASON = 300;
    private static final int MAX_ERROR = 1000;

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
    @Enumerated(EnumType.STRING) @Column(name = "message_kind") private MessageKind messageKind;
    @Column(name = "classification_reason") private String classificationReason;
    @Column(name = "next_attempt_at") private Instant nextAttemptAt;
    @Column(name = "locked_until") private Instant lockedUntil;

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
    public Instant getReceivedAt() { return receivedAt; }
    public ProcessingStatus getProcessingStatus() { return processingStatus; }
    public int getProcessingAttempts() { return processingAttempts; }
    public String getLastProcessingError() { return lastProcessingError; }
    public MessageKind getMessageKind() { return messageKind; }
    public String getClassificationReason() { return classificationReason; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }

    /** Body and title together: the text an extraction is allowed to draw numbers and digits from. */
    public String searchableText() { return title == null || title.isBlank() ? body : title + "\n" + body; }

    public void markProcessed(MessageKind kind, String reason) { finish(ProcessingStatus.PROCESSED, kind, reason); lastProcessingError = null; }
    public void markIgnored(MessageKind kind, String reason) { finish(ProcessingStatus.IGNORED, kind, reason); lastProcessingError = null; }
    public void markRetry(String error, Instant nextAttempt) { processingStatus = ProcessingStatus.RETRY; lastProcessingError = truncate(error, MAX_ERROR); nextAttemptAt = nextAttempt; lockedUntil = null; }
    public void markFailed(String error) { finish(ProcessingStatus.FAILED, messageKind, classificationReason); lastProcessingError = truncate(error, MAX_ERROR); }
    public void resetForReprocessing() {
        processingStatus = ProcessingStatus.RECEIVED; processingAttempts = 0; lastProcessingError = null; messageKind = null; classificationReason = null; nextAttemptAt = null; lockedUntil = null;
    }

    private void finish(ProcessingStatus status, MessageKind kind, String reason) {
        processingStatus = status; messageKind = kind; classificationReason = truncate(reason, MAX_REASON); nextAttemptAt = null; lockedUntil = null;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
