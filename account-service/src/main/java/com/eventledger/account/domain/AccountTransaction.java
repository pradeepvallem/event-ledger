package com.eventledger.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;

@Getter
@Entity
@Table(
        name = "account_transactions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_account_transactions_event_id",
                        columnNames = "event_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_transactions_account_timestamp",
                        columnList = "account_id, event_timestamp"
                ),
                @Index(
                        name = "idx_transactions_account_type",
                        columnList = "account_id, transaction_type"
                )
        }
)
public class AccountTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(
            name = "event_id",
            nullable = false,
            updatable = false,
            length = 100
    )
    private String eventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "account_id",
            nullable = false,
            updatable = false
    )
    private Account account;

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

    @Column(
            name = "received_at",
            nullable = false,
            updatable = false
    )
    private Instant receivedAt;

    protected AccountTransaction() {
        // Required by JPA.
    }

    public AccountTransaction(
            String eventId,
            Account account,
            TransactionType type,
            BigDecimal amount,
            String currency,
            Instant eventTimestamp
    ) {
        this.eventId = eventId;
        this.account = account;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.eventTimestamp = eventTimestamp;
    }

    @PrePersist
    void initializeReceivedAt() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof AccountTransaction transaction)) {
            return false;
        }

        return Objects.equals(eventId, transaction.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }
}