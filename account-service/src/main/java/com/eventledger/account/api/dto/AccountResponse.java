package com.eventledger.account.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AccountResponse(
        String accountId,
        String currency,
        BigDecimal balance,
        Instant createdAt,
        List<TransactionResponse> recentTransactions
) {
}