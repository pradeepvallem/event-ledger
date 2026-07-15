package com.eventledger.account.exception;

public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(
            String accountId,
            String expectedCurrency,
            String providedCurrency
    ) {
        super(
                "Currency mismatch for account %s: expected %s but received %s"
                        .formatted(accountId, expectedCurrency, providedCurrency)
        );
    }
}