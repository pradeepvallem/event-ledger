package com.eventledger.gateway.client;

import com.eventledger.gateway.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountTransactionResponse(
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