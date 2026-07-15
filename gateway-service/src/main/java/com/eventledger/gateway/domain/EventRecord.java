package com.eventledger.gateway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import lombok.Getter;

@Getter
@Entity
@Table(
        name = "event_records",
        indexes = {
                @Index(
                        name = "idx_events_account_timestamp",
                        columnList = "account_id, event_timestamp"
                ),
                @Index(
                        name = "idx_events_status",
                        columnList = "event_status"
                ),
                @Index(
                        name = "idx_events_received_at",
                        columnList = "received_at"
                )
        }
)
public class EventRecord {

    @Id
    @Column(
            name = "event_id",
            nullable = false,
            updatable = false,
            length = 100
    )
    private String eventId;

    @Column(
            name = "account_id",
            nullable = false,
            updatable = false,
            length = 100
    )
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "transaction_type",
            nullable = false,
            updatable = false,
            length = 20
    )
    private TransactionType type;

    @Column(
            name = "amount",
            nullable = false,
            updatable = false,
            precision = 19,
            scale = 4
    )
    private BigDecimal amount;

    @Column(
            name = "currency",
            nullable = false,
            updatable = false,
            length = 3
    )
    private String currency;

    @Column(
            name = "event_timestamp",
            nullable = false,
            updatable = false
    )
    private Instant eventTimestamp;

    /**
     * Stored as JSON text for portability with H2.
     *
     * We will serialize the metadata map before persistence and deserialize
     * it when building responses.
     */
    @Lob
    @Column(
            name = "metadata_json",
            columnDefinition = "CLOB"
    )
    private String metadataJson;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "event_status",
            nullable = false,
            length = 20
    )
    private EventStatus status;

    @Column(
            name = "received_at",
            nullable = false,
            updatable = false
    )
    private Instant receivedAt;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "last_failure_reason", length = 1000)
    private String lastFailureReason;

    protected EventRecord() {
        // Required by JPA.
    }

    public EventRecord(
            String eventId,
            String accountId,
            TransactionType type,
            BigDecimal amount,
            String currency,
            Instant eventTimestamp,
            String metadataJson
    ) {
        this.eventId = Objects.requireNonNull(eventId).trim();
        this.accountId = Objects.requireNonNull(accountId).trim();
        this.type = Objects.requireNonNull(type);
        this.amount = Objects.requireNonNull(amount);
        this.currency = Objects.requireNonNull(currency)
                .trim()
                .toUpperCase(Locale.ROOT);
        this.eventTimestamp = Objects.requireNonNull(eventTimestamp);
        this.metadataJson = metadataJson;
        this.status = EventStatus.RECEIVED;
    }

    @PrePersist
    void initializeReceivedAt() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }

        if (status == null) {
            status = EventStatus.RECEIVED;
        }
    }

    public void markApplied(Instant appliedAt) {
        this.status = EventStatus.APPLIED;
        this.appliedAt = Objects.requireNonNull(appliedAt);
        this.lastFailureReason = null;
    }

    public void markFailed(String failureReason) {
        this.status = EventStatus.FAILED;
        this.lastFailureReason = truncateFailureReason(failureReason);
    }

    private String truncateFailureReason(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            return "Account Service request failed";
        }

        String normalized = failureReason.trim();

        return normalized.length() <= 1000
                ? normalized
                : normalized.substring(0, 1000);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof EventRecord eventRecord)) {
            return false;
        }

        return Objects.equals(eventId, eventRecord.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }
}