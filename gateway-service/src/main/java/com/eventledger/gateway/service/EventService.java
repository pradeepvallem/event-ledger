package com.eventledger.gateway.service;

import com.eventledger.gateway.api.dto.EventResponse;
import com.eventledger.gateway.api.dto.SubmitEventRequest;
import com.eventledger.gateway.client.AccountGateway;
import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import com.eventledger.gateway.exception.ConflictingEventException;
import com.eventledger.gateway.exception.EventNotFoundException;
import com.eventledger.gateway.repository.EventRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.eventledger.gateway.observability.EventMetrics;
import com.eventledger.gateway.domain.EventStatus;
import com.eventledger.gateway.exception.EventNotAppliedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class EventService {

    private final EventRecordRepository eventRepository;
    private final EventPersistenceService persistenceService;
    private final AccountGateway accountGateway;
    private final MetadataConverter metadataConverter;
    private final EventMapper eventMapper;
    private final EventReplayValidator replayValidator;
    private final EventMetrics eventMetrics;

    private static final Logger log =
            LoggerFactory.getLogger(EventService.class);

    public EventService(
            EventRecordRepository eventRepository,
            EventPersistenceService persistenceService,
            AccountGateway accountGateway,
            MetadataConverter metadataConverter,
            EventMapper eventMapper,
            EventReplayValidator replayValidator,
            EventMetrics eventMetrics
    ) {
        this.eventRepository = eventRepository;
        this.persistenceService = persistenceService;
        this.accountGateway = accountGateway;
        this.metadataConverter = metadataConverter;
        this.eventMapper = eventMapper;
        this.replayValidator = replayValidator;
        this.eventMetrics = eventMetrics;
    }

    public EventSubmissionResult submitEvent(
            SubmitEventRequest request
    ) {
        NormalizedEvent normalized = normalize(request);
        log.atInfo()
                .addKeyValue("eventId", normalized.eventId())
                .addKeyValue("accountId", normalized.accountId())
                .addKeyValue("transactionType", normalized.type())
                .log("Processing event");

        EventRecord existing =
                eventRepository.findById(normalized.eventId())
                        .orElse(null);


        if (existing != null) {
            return handleExistingEvent(existing, normalized);
        }

        SavedEvent savedEvent =
                insertEventRaceSafely(normalized);

        if (!savedEvent.created()) {
            return handleExistingEvent(
                    savedEvent.event(),
                    normalized
            );
        }

        EventRecord event = savedEvent.event();
        eventMetrics.recordReceived();
        log.atInfo()
                .addKeyValue("eventId", event.getEventId())
                .addKeyValue("accountId", event.getAccountId())
                .addKeyValue("eventStatus", "APPLIED")
                .log("Event applied successfully");

        try {
            accountGateway.applyTransaction(event);
            eventMetrics.recordApplied();

            persistenceService.markApplied(event.getEventId());

            EventRecord appliedEvent =
                    eventRepository.findById(event.getEventId())
                            .orElseThrow(() ->
                                    new EventNotFoundException(
                                            event.getEventId()
                                    )
                            );

            return new EventSubmissionResult(
                    eventMapper.toResponse(appliedEvent, false),
                    true
            );
        } catch (AccountServiceUnavailableException exception) {
            persistenceService.markFailed(
                    event.getEventId(),
                    exception.getMessage()
            );

            eventMetrics.recordFailed();
            log.atWarn()
                    .addKeyValue("eventId", event.getEventId())
                    .addKeyValue("accountId", event.getAccountId())
                    .addKeyValue("eventStatus", "FAILED")
                    .addKeyValue(
                            "failureReason",
                            exception.getMessage()
                    )
                    .log("Account Service call failed");

            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(String eventId) {
        String normalizedEventId =
                normalizeRequired(eventId, "eventId");

        EventRecord event =
                eventRepository.findById(normalizedEventId)
                        .orElseThrow(() ->
                                new EventNotFoundException(
                                        normalizedEventId
                                )
                        );

        return eventMapper.toResponse(event, false);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getEventsForAccount(
            String accountId
    ) {
        String normalizedAccountId =
                normalizeRequired(accountId, "accountId");

        return eventRepository
                .findByAccountIdOrderByEventTimestampAscEventIdAsc(
                        normalizedAccountId
                )
                .stream()
                .map(event ->
                        eventMapper.toResponse(event, false)
                )
                .toList();
    }

    private SavedEvent insertEventRaceSafely(
            NormalizedEvent normalized
    ) {
        EventRecord newEvent = new EventRecord(
                normalized.eventId(),
                normalized.accountId(),
                normalized.type(),
                normalized.amount(),
                normalized.currency(),
                normalized.eventTimestamp(),
                metadataConverter.serialize(
                        normalized.metadata()
                )
        );

        try {
            EventRecord saved =
                    persistenceService.insert(newEvent);

            return new SavedEvent(saved, true);
        } catch (DataIntegrityViolationException exception) {
            EventRecord concurrentEvent =
                    eventRepository
                            .findById(normalized.eventId())
                            .orElseThrow(() -> exception);

            return new SavedEvent(
                    concurrentEvent,
                    false
            );
        }
    }

    private NormalizedEvent normalize(
            SubmitEventRequest request
    ) {
        return new NormalizedEvent(
                normalizeRequired(request.eventId(), "eventId"),
                normalizeRequired(
                        request.accountId(),
                        "accountId"
                ),
                request.type(),
                request.amount(),
                request.currency()
                        .trim()
                        .toUpperCase(Locale.ROOT),
                request.eventTimestamp(),
                request.metadata() == null
                        ? Map.of()
                        : Map.copyOf(request.metadata())
        );
    }

    private EventSubmissionResult handleExistingEvent(
            EventRecord existing,
            NormalizedEvent normalized
    ) {
        try {
            replayValidator.validateIdenticalReplay(
                    existing,
                    normalized
            );
        } catch (ConflictingEventException exception) {
            eventMetrics.recordConflict();
            throw exception;
        }

        eventMetrics.recordReplayed();

        if (existing.getStatus() != EventStatus.APPLIED) {
            throw new EventNotAppliedException(
                    existing.getEventId(),
                    existing.getStatus(),
                    existing.getLastFailureReason()
            );
        }

        log.atInfo()
                .addKeyValue("eventId", existing.getEventId())
                .addKeyValue("eventStatus", existing.getStatus())
                .addKeyValue("idempotentReplay", true)
                .log("Returning applied event replay");

        return new EventSubmissionResult(
                eventMapper.toResponse(existing, true),
                false
        );
    }

    private String normalizeRequired(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        return value.trim();
    }

    private record SavedEvent(
            EventRecord event,
            boolean created
    ) {
    }
}