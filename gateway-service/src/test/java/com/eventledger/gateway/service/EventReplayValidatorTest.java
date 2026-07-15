package com.eventledger.gateway.service;

import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.domain.TransactionType;
import com.eventledger.gateway.exception.ConflictingEventException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventReplayValidatorTest {

    private static final String EVENT_ID = "evt-001";
    private static final String ACCOUNT_ID = "acct-123";
    private static final String CURRENCY = "USD";

    private static final Instant EVENT_TIMESTAMP =
            Instant.parse("2026-05-15T14:02:11Z");

    private MetadataConverter metadataConverter;
    private EventReplayValidator replayValidator;

    @BeforeEach
    void setUp() {
        metadataConverter = mock(MetadataConverter.class);
        replayValidator =
                new EventReplayValidator(metadataConverter);
    }

    @Test
    @DisplayName("accepts an identical event replay")
    void shouldAcceptIdenticalReplay() {
        EventRecord existing = existingEvent();

        Map<String, Object> metadata = Map.of(
                "source", "mainframe-batch",
                "batchId", "B-9042"
        );

        NormalizedEvent replay = normalizedEvent(
                ACCOUNT_ID,
                TransactionType.CREDIT,
                "150.00",
                CURRENCY,
                EVENT_TIMESTAMP,
                metadata
        );

        when(
                metadataConverter.deserialize(
                        existing.getMetadataJson()
                )
        ).thenReturn(metadata);

        assertThatCode(() ->
                replayValidator.validateIdenticalReplay(
                        existing,
                        replay
                )
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName(
            "treats monetary values with different scales as identical"
    )
    void shouldCompareAmountsByNumericValue() {
        EventRecord existing = existingEvent();

        Map<String, Object> metadata = Map.of(
                "source", "mainframe-batch"
        );

        NormalizedEvent replay = normalizedEvent(
                ACCOUNT_ID,
                TransactionType.CREDIT,
                "150.0",
                CURRENCY,
                EVENT_TIMESTAMP,
                metadata
        );

        when(
                metadataConverter.deserialize(
                        existing.getMetadataJson()
                )
        ).thenReturn(metadata);

        assertThatCode(() ->
                replayValidator.validateIdenticalReplay(
                        existing,
                        replay
                )
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects replay with a different account")
    void shouldRejectDifferentAccount() {
        EventRecord existing = existingEvent();

        NormalizedEvent replay = normalizedEvent(
                "acct-other",
                TransactionType.CREDIT,
                "150.00",
                CURRENCY,
                EVENT_TIMESTAMP,
                standardMetadata()
        );

        assertConflict(existing, replay);
    }

    @Test
    @DisplayName("rejects replay with a different transaction type")
    void shouldRejectDifferentType() {
        EventRecord existing = existingEvent();

        NormalizedEvent replay = normalizedEvent(
                ACCOUNT_ID,
                TransactionType.DEBIT,
                "150.00",
                CURRENCY,
                EVENT_TIMESTAMP,
                standardMetadata()
        );

        assertConflict(existing, replay);
    }

    @Test
    @DisplayName("rejects replay with a different amount")
    void shouldRejectDifferentAmount() {
        EventRecord existing = existingEvent();

        NormalizedEvent replay = normalizedEvent(
                ACCOUNT_ID,
                TransactionType.CREDIT,
                "999.00",
                CURRENCY,
                EVENT_TIMESTAMP,
                standardMetadata()
        );

        assertConflict(existing, replay);
    }

    @Test
    @DisplayName("rejects replay with a different currency")
    void shouldRejectDifferentCurrency() {
        EventRecord existing = existingEvent();

        NormalizedEvent replay = normalizedEvent(
                ACCOUNT_ID,
                TransactionType.CREDIT,
                "150.00",
                "EUR",
                EVENT_TIMESTAMP,
                standardMetadata()
        );

        assertConflict(existing, replay);
    }

    @Test
    @DisplayName("rejects replay with a different timestamp")
    void shouldRejectDifferentTimestamp() {
        EventRecord existing = existingEvent();

        NormalizedEvent replay = normalizedEvent(
                ACCOUNT_ID,
                TransactionType.CREDIT,
                "150.00",
                CURRENCY,
                Instant.parse("2026-05-15T15:00:00Z"),
                standardMetadata()
        );

        assertConflict(existing, replay);
    }

    @Test
    @DisplayName("rejects replay with different metadata")
    void shouldRejectDifferentMetadata() {
        EventRecord existing = existingEvent();

        Map<String, Object> existingMetadata = Map.of(
                "source", "mainframe-batch",
                "batchId", "B-9042"
        );

        Map<String, Object> replayMetadata = Map.of(
                "source", "api",
                "batchId", "B-9042"
        );

        when(
                metadataConverter.deserialize(
                        existing.getMetadataJson()
                )
        ).thenReturn(existingMetadata);

        NormalizedEvent replay = normalizedEvent(
                ACCOUNT_ID,
                TransactionType.CREDIT,
                "150.00",
                CURRENCY,
                EVENT_TIMESTAMP,
                replayMetadata
        );

        assertThatThrownBy(() ->
                replayValidator.validateIdenticalReplay(
                        existing,
                        replay
                )
        )
                .isInstanceOf(ConflictingEventException.class)
                .hasMessageContaining(EVENT_ID)
                .hasMessageContaining("different event data");
    }

    private void assertConflict(
            EventRecord existing,
            NormalizedEvent replay
    ) {
        when(
                metadataConverter.deserialize(
                        existing.getMetadataJson()
                )
        ).thenReturn(replay.metadata());

        assertThatThrownBy(() ->
                replayValidator.validateIdenticalReplay(
                        existing,
                        replay
                )
        )
                .isInstanceOf(ConflictingEventException.class)
                .hasMessageContaining(EVENT_ID);
    }

    private EventRecord existingEvent() {
        return new EventRecord(
                EVENT_ID,
                ACCOUNT_ID,
                TransactionType.CREDIT,
                new BigDecimal("150.00"),
                CURRENCY,
                EVENT_TIMESTAMP,
                """
                {
                  "source": "mainframe-batch",
                  "batchId": "B-9042"
                }
                """
        );
    }

    private NormalizedEvent normalizedEvent(
            String accountId,
            TransactionType type,
            String amount,
            String currency,
            Instant timestamp,
            Map<String, Object> metadata
    ) {
        return new NormalizedEvent(
                EVENT_ID,
                accountId,
                type,
                new BigDecimal(amount),
                currency,
                timestamp,
                metadata
        );
    }

    private Map<String, Object> standardMetadata() {
        return Map.of(
                "source", "mainframe-batch",
                "batchId", "B-9042"
        );
    }
}