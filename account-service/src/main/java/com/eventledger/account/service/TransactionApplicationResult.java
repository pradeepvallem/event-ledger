package com.eventledger.account.service;

import com.eventledger.account.api.dto.TransactionResponse;

public record TransactionApplicationResult(
        TransactionResponse transaction,
        boolean created
) {
}