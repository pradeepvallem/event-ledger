package com.eventledger.gateway.api.dto;

import com.eventledger.gateway.domain.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record SubmitEventRequest(

        @NotBlank(message = "eventId is required")
        @Size(
                max = 100,
                message = "eventId must not exceed 100 characters"
        )
        String eventId,

        @NotBlank(message = "accountId is required")
        @Size(
                max = 100,
                message = "accountId must not exceed 100 characters"
        )
        String accountId,

        @NotNull(message = "type is required")
        TransactionType type,

        @NotNull(message = "amount is required")
        @DecimalMin(
                value = "0.0",
                inclusive = false,
                message = "amount must be greater than zero"
        )
        @Digits(
                integer = 15,
                fraction = 4,
                message = "amount must contain at most 15 integer digits and 4 decimal places"
        )
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Pattern(
                regexp = "^[A-Za-z]{3}$",
                message = "currency must be a three-letter code"
        )
        String currency,

        @NotNull(message = "eventTimestamp is required")
        Instant eventTimestamp,

        Map<String, Object> metadata
) {
}