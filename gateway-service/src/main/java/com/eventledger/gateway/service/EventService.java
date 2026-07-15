package com.eventledger.gateway.service;

import com.eventledger.gateway.api.dto.EventResponse;
import com.eventledger.gateway.api.dto.SubmitEventRequest;
import com.eventledger.gateway.client.AccountGateway;
import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import com.eventledger.gateway.exception.EventNotFoundException;
import com.eventledger.gateway.repository.EventRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public EventService(
            EventRecordRepository eventRepository,
            EventPersistenceService persistenceService,
            AccountGateway accountGateway,
            MetadataConverter metadataConverter,
            EventMapper eventMapper,
            EventReplayValidator replayValidator
    ) {
        this.eventRepository = eventRepository;
        this.persistenceService = persistenceService;
        this.accountGateway = accountGateway;
        this.metadataConverter = metadataConverter;
        this.eventMapper = eventMapper;
        this.replayValidator = replayValidator;
    }

    public EventSubmissionResult submitEvent(
            SubmitEventRequest request
    ) {
        NormalizedEvent normalized = normalize(request);

        EventRecord existing =
                eventRepository.findById(normalized.eventId())
                        .orElse(null);

        if (existing != null) {
            replayValidator.validateIdenticalReplay(
                    existing,
                    normalized
            );

            return new EventSubmissionResult(
                    eventMapper.toResponse(existing, true),
                    false
            );
        }

        SavedEvent savedEvent =
                insertEventRaceSafely(normalized);

        if (!savedEvent.created()) {
            replayValidator.validateIdenticalReplay(
                    savedEvent.event(),
                    normalized
            );

            return new EventSubmissionResult(
                    eventMapper.toResponse(
                            savedEvent.event(),
                            true
                    ),
                    false
            );
        }

        EventRecord event = savedEvent.event();

        try {
            accountGateway.applyTransaction(event);

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