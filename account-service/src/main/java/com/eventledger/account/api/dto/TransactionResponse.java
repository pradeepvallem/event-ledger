package com.eventledger.account.api.dto;

import com.eventledger.account.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        String eventId,
        String accountId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp,
        Instant receivedAt,
        boolean idempotentReplay
) {
}