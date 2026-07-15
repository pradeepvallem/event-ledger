package com.eventledger.account.api.dto;

import java.math.BigDecimal;

public record BalanceResponse(
        String accountId,
        String currency,
        BigDecimal balance
) {
}