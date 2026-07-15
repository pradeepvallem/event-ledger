package com.eventledger.gateway.service;

import com.eventledger.gateway.api.dto.EventResponse;
import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.domain.EventStatus;
import com.eventledger.gateway.domain.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventMapperTest {

    private static final String EVENT_ID = "evt-001";
    private static final String ACCOUNT_ID = "acct-123";
    private static final String METADATA_JSON =
            """
            {
              "source": "mainframe-batch",
              "batchId": "B-9042"
            }
            """;

    private static final Instant EVENT_TIMESTAMP =
            Instant.parse("2026-05-15T14:02:11Z");

    private MetadataConverter metadataConverter;
    private EventMapper eventMapper;

    @BeforeEach
    void setUp() {
        metadataConverter = mock(MetadataConverter.class);
        eventMapper = new EventMapper(metadataConverter);
    }

    @Test
    @DisplayName("maps a received event to its response")
    void shouldMapReceivedEvent() {
        EventRecord event = new EventRecord(
                EVENT_ID,
                ACCOUNT_ID,
                TransactionType.CREDIT,
                new BigDecimal("150.00"),
                "USD",
                EVENT_TIMESTAMP,
                METADATA_JSON
        );

        Map<String, Object> metadata = Map.of(
                "source", "mainframe-batch",
                "batchId", "B-9042"
        );

        when(metadataConverter.deserialize(METADATA_JSON))
                .thenReturn(metadata);

        EventResponse response =
                eventMapper.toResponse(event, false);

        assertThat(response.eventId()).isEqualTo(EVENT_ID);
        assertThat(response.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(response.type())
                .isEqualTo(TransactionType.CREDIT);
        assertThat(response.amount())
                .isEqualByComparingTo("150.00");
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.eventTimestamp())
                .isEqualTo(EVENT_TIMESTAMP);
        assertThat(response.metadata()).isEqualTo(metadata);
        assertThat(response.status())
                .isEqualTo(EventStatus.RECEIVED);
        assertThat(response.appliedAt()).isNull();
        assertThat(response.failureReason()).isNull();
        assertThat(response.idempotentReplay()).isFalse();

        verify(metadataConverter).deserialize(METADATA_JSON);
    }

    @Test
    @DisplayName("maps an applied event including applied timestamp")
    void shouldMapAppliedEvent() {
        EventRecord event = new EventRecord(
                EVENT_ID,
                ACCOUNT_ID,
                TransactionType.CREDIT,
                new BigDecimal("150.00"),
                "USD",
                EVENT_TIMESTAMP,
                METADATA_JSON
        );

        Instant appliedAt =
                Instant.parse("2026-07-15T06:00:00Z");

        event.markApplied(appliedAt);

        when(metadataConverter.deserialize(METADATA_JSON))
                .thenReturn(Map.of());

        EventResponse response =
                eventMapper.toResponse(event, false);

        assertThat(response.status())
                .isEqualTo(EventStatus.APPLIED);
        assertThat(response.appliedAt())
                .isEqualTo(appliedAt);
        assertThat(response.failureReason()).isNull();
    }

    @Test
    @DisplayName("maps a failed event including its failure reason")
    void shouldMapFailedEvent() {
        EventRecord event = new EventRecord(
                EVENT_ID,
                ACCOUNT_ID,
                TransactionType.DEBIT,
                new BigDecimal("25.00"),
                "USD",
                EVENT_TIMESTAMP,
                METADATA_JSON
        );

        event.markFailed("Account Service is unavailable");

        when(metadataConverter.deserialize(METADATA_JSON))
                .thenReturn(Map.of());

        EventResponse response =
                eventMapper.toResponse(event, false);

        assertThat(response.status())
                .isEqualTo(EventStatus.FAILED);
        assertThat(response.failureReason())
                .isEqualTo("Account Service is unavailable");
        assertThat(response.appliedAt()).isNull();
    }

    @Test
    @DisplayName("sets idempotent replay flag when requested")
    void shouldSetIdempotentReplayFlag() {
        EventRecord event = new EventRecord(
                EVENT_ID,
                ACCOUNT_ID,
                TransactionType.CREDIT,
                new BigDecimal("150.00"),
                "USD",
                EVENT_TIMESTAMP,
                METADATA_JSON
        );

        when(metadataConverter.deserialize(METADATA_JSON))
                .thenReturn(Map.of());

        EventResponse response =
                eventMapper.toResponse(event, true);

        assertThat(response.idempotentReplay()).isTrue();
    }
}