package com.eventledger.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import lombok.Getter;

@Getter
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @Column(
            name = "account_id",
            nullable = false,
            updatable = false,
            length = 100
    )
    private String accountId;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    protected Account() {
        // Required by JPA.
    }

    public Account(String accountId) {
        this.accountId = accountId;
    }

    @PrePersist
    void initializeCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof Account account)) {
            return false;
        }

        return Objects.equals(accountId, account.accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId);
    }
}