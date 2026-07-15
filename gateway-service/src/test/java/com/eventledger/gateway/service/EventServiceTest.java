package com.eventledger.gateway.service;

import com.eventledger.gateway.api.dto.EventResponse;
import com.eventledger.gateway.api.dto.SubmitEventRequest;
import com.eventledger.gateway.client.AccountGateway;
import com.eventledger.gateway.client.AccountTransactionResponse;
import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.domain.EventStatus;
import com.eventledger.gateway.domain.TransactionType;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import com.eventledger.gateway.exception.EventNotFoundException;
import com.eventledger.gateway.observability.EventMetrics;
import com.eventledger.gateway.repository.EventRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    private static final String EVENT_ID = "evt-001";
    private static final String ACCOUNT_ID = "acct-123";
    private static final String METADATA_JSON =
            "{\"source\":\"mainframe-batch\"}";

    private static final Instant EVENT_TIMESTAMP =
            Instant.parse("2026-05-15T14:02:11Z");

    @Mock
    private EventRecordRepository eventRepository;

    @Mock
    private EventPersistenceService persistenceService;

    @Mock
    private AccountGateway accountGateway;

    @Mock
    private MetadataConverter metadataConverter;

    @Mock
    private EventMapper eventMapper;

    @Mock
    private EventReplayValidator replayValidator;

    @Mock
    private EventMetrics eventMetrics;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(
                eventRepository,
                persistenceService,
                accountGateway,
                metadataConverter,
                eventMapper,
                replayValidator,
                eventMetrics
        );
    }

    @Test
    @DisplayName("persists and applies a new event")
    void shouldPersistAndApplyNewEvent() {
        SubmitEventRequest request = request();

        EventRecord receivedEvent = receivedEvent();
        EventRecord appliedEvent = receivedEvent();
        appliedEvent.markApplied(
                Instant.parse("2026-07-15T07:00:00Z")
        );

        EventResponse response = response(
                EventStatus.APPLIED,
                false
        );

        when(eventRepository.findById(EVENT_ID))
                .thenReturn(
                        Optional.empty(),
                        Optional.of(appliedEvent)
                );

        when(metadataConverter.serialize(request.metadata()))
                .thenReturn(METADATA_JSON);

        when(persistenceService.insert(any(EventRecord.class)))
                .thenReturn(receivedEvent);

        when(accountGateway.applyTransaction(receivedEvent))
                .thenReturn(accountResponse());

        when(eventMapper.toResponse(appliedEvent, false))
                .thenReturn(response);

        EventSubmissionResult result =
                eventService.submitEvent(request);

        assertThat(result.created()).isTrue();
        assertThat(result.event().status())
                .isEqualTo(EventStatus.APPLIED);
        assertThat(result.event().idempotentReplay())
                .isFalse();

        verify(persistenceService).insert(
                any(EventRecord.class)
        );
        verify(accountGateway)
                .applyTransaction(receivedEvent);
        verify(persistenceService)
                .markApplied(EVENT_ID);
        verify(persistenceService, never())
                .markFailed(any(), any());
        verify(eventMetrics).recordReceived();
        verify(eventMetrics).recordApplied();

        verify(eventMetrics, never()).recordFailed();
        verify(eventMetrics, never()).recordReplayed();
        verify(eventMetrics, never()).recordConflict();
    }

    @Test
    @DisplayName(
            "returns existing identical event without calling Account Service"
    )
    void shouldReturnExistingIdempotentReplay() {
        SubmitEventRequest request = request();
        EventRecord existing = receivedEvent();

        EventResponse response = response(
                EventStatus.APPLIED,
                true
        );

        when(eventRepository.findById(EVENT_ID))
                .thenReturn(Optional.of(existing));

        when(eventMapper.toResponse(existing, true))
                .thenReturn(response);

        EventSubmissionResult result =
                eventService.submitEvent(request);

        assertThat(result.created()).isFalse();
        assertThat(result.event().idempotentReplay())
                .isTrue();

        verify(replayValidator)
                .validateIdenticalReplay(
                        any(EventRecord.class),
                        any(NormalizedEvent.class)
                );

        verifyNoInteractions(
                persistenceService,
                accountGateway,
                metadataConverter
        );
        verify(eventMetrics).recordReplayed();

        verify(eventMetrics, never()).recordReceived();
        verify(eventMetrics, never()).recordApplied();
    }

    @Test
    @DisplayName(
            "handles a concurrent insert as an idempotent replay"
    )
    void shouldRecoverFromConcurrentInsert() {
        SubmitEventRequest request = request();
        EventRecord concurrentEvent = receivedEvent();

        EventResponse response = response(
                EventStatus.APPLIED,
                true
        );

        when(eventRepository.findById(EVENT_ID))
                .thenReturn(
                        Optional.empty(),
                        Optional.of(concurrentEvent)
                );

        when(metadataConverter.serialize(request.metadata()))
                .thenReturn(METADATA_JSON);

        when(persistenceService.insert(any(EventRecord.class)))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "duplicate event ID"
                        )
                );

        when(eventMapper.toResponse(concurrentEvent, true))
                .thenReturn(response);

        EventSubmissionResult result =
                eventService.submitEvent(request);

        assertThat(result.created()).isFalse();
        assertThat(result.event().idempotentReplay())
                .isTrue();

        verify(replayValidator)
                .validateIdenticalReplay(
                        any(EventRecord.class),
                        any(NormalizedEvent.class)
                );

        verifyNoInteractions(accountGateway);
        verify(eventMetrics).recordReplayed();

        verify(eventMetrics, never()).recordReceived();
        verify(eventMetrics, never()).recordApplied();
    }

    @Test
    @DisplayName(
            "marks a new event failed when Account Service is unavailable"
    )
    void shouldMarkEventFailedWhenAccountServiceUnavailable() {
        SubmitEventRequest request = request();
        EventRecord event = receivedEvent();

        when(eventRepository.findById(EVENT_ID))
                .thenReturn(Optional.empty());

        when(metadataConverter.serialize(request.metadata()))
                .thenReturn(METADATA_JSON);

        when(persistenceService.insert(any(EventRecord.class)))
                .thenReturn(event);

        AccountServiceUnavailableException exception =
                new AccountServiceUnavailableException(
                        "Account Service is unavailable",
                        new RuntimeException("connection refused")
                );

        when(accountGateway.applyTransaction(event))
                .thenThrow(exception);

        assertThatThrownBy(() ->
                eventService.submitEvent(request)
        )
                .isSameAs(exception)
                .hasMessage("Account Service is unavailable");

        verify(persistenceService).markFailed(
                EVENT_ID,
                "Account Service is unavailable"
        );

        verify(persistenceService, never())
                .markApplied(any());
        verify(eventMetrics).recordReceived();
        verify(eventMetrics).recordFailed();

        verify(eventMetrics, never()).recordApplied();
    }

    @Test
    @DisplayName("returns an event by event ID")
    void shouldReturnEventById() {
        EventRecord event = receivedEvent();
        EventResponse response = response(
                EventStatus.RECEIVED,
                false
        );

        when(eventRepository.findById(EVENT_ID))
                .thenReturn(Optional.of(event));

        when(eventMapper.toResponse(event, false))
                .thenReturn(response);

        EventResponse result =
                eventService.getEvent("  evt-001  ");

        assertThat(result.eventId()).isEqualTo(EVENT_ID);

        verify(eventRepository).findById(EVENT_ID);
    }

    @Test
    @DisplayName("throws when an event does not exist")
    void shouldThrowWhenEventDoesNotExist() {
        when(eventRepository.findById("evt-missing"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                eventService.getEvent("evt-missing")
        )
                .isInstanceOf(EventNotFoundException.class)
                .hasMessage("Event not found: evt-missing");

        verifyNoInteractions(eventMapper);
    }

    @Test
    @DisplayName(
            "returns account events in repository order"
    )
    void shouldReturnEventsForAccount() {
        EventRecord first = event(
                "evt-001",
                Instant.parse("2026-05-15T09:00:00Z")
        );

        EventRecord second = event(
                "evt-002",
                Instant.parse("2026-05-15T11:00:00Z")
        );

        EventResponse firstResponse = responseFor(
                first,
                EventStatus.RECEIVED
        );

        EventResponse secondResponse = responseFor(
                second,
                EventStatus.RECEIVED
        );

        when(
                eventRepository
                        .findByAccountIdOrderByEventTimestampAscEventIdAsc(
                                ACCOUNT_ID
                        )
        ).thenReturn(List.of(first, second));

        when(eventMapper.toResponse(first, false))
                .thenReturn(firstResponse);

        when(eventMapper.toResponse(second, false))
                .thenReturn(secondResponse);

        List<EventResponse> responses =
                eventService.getEventsForAccount(
                        "  acct-123  "
                );

        assertThat(responses)
                .extracting(EventResponse::eventId)
                .containsExactly("evt-001", "evt-002");
    }

    private SubmitEventRequest request() {
        return new SubmitEventRequest(
                EVENT_ID,
                ACCOUNT_ID,
                TransactionType.CREDIT,
                new BigDecimal("150.00"),
                "usd",
                EVENT_TIMESTAMP,
                Map.of("source", "mainframe-batch")
        );
    }

    private EventRecord receivedEvent() {
        return event(EVENT_ID, EVENT_TIMESTAMP);
    }

    private EventRecord event(
            String eventId,
            Instant timestamp
    ) {
        return new EventRecord(
                eventId,
                ACCOUNT_ID,
                TransactionType.CREDIT,
                new BigDecimal("150.00"),
                "USD",
                timestamp,
                METADATA_JSON
        );
    }

    private EventResponse response(
            EventStatus status,
            boolean idempotentReplay
    ) {
        return new EventResponse(
                EVENT_ID,
                ACCOUNT_ID,
                TransactionType.CREDIT,
                new BigDecimal("150.00"),
                "USD",
                EVENT_TIMESTAMP,
                Map.of("source", "mainframe-batch"),
                status,
                Instant.parse("2026-07-15T06:00:00Z"),
                status == EventStatus.APPLIED
                        ? Instant.parse("2026-07-15T07:00:00Z")
                        : null,
                null,
                idempotentReplay
        );
    }

    private EventResponse responseFor(
            EventRecord event,
            EventStatus status
    ) {
        return new EventResponse(
                event.getEventId(),
                event.getAccountId(),
                event.getType(),
                event.getAmount(),
                event.getCurrency(),
                event.getEventTimestamp(),
                Map.of(),
                status,
                null,
                null,
                null,
                false
        );
    }

    private AccountTransactionResponse accountResponse() {
        return new AccountTransactionResponse(
                EVENT_ID,
                ACCOUNT_ID,
                TransactionType.CREDIT,
                new BigDecimal("150.00"),
                "USD",
                EVENT_TIMESTAMP,
                Instant.parse("2026-07-15T06:00:00Z"),
                false
        );
    }
}