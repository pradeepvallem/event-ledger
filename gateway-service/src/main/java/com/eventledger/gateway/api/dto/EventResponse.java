package com.eventledger.gateway.api.dto;

import com.eventledger.gateway.domain.EventStatus;
import com.eventledger.gateway.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record EventResponse(
        String eventId,
        String accountId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp,
        Map<String, Object> metadata,
        EventStatus status,
        Instant receivedAt,
        Instant appliedAt,
        String failureReason,
        boolean idempotentReplay
) {

    public EventResponse {
        metadata = metadata == null
                ? Map.of()
                : Map.copyOf(metadata);
    }
}