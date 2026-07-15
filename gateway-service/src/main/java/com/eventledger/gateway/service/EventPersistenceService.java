package com.eventledger.gateway.service;

import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.exception.EventNotFoundException;
import com.eventledger.gateway.repository.EventRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class EventPersistenceService {

    private final EventRecordRepository eventRepository;

    public EventPersistenceService(
            EventRecordRepository eventRepository
    ) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public EventRecord insert(EventRecord event) {
        return eventRepository.saveAndFlush(event);
    }

    @Transactional
    public void markApplied(String eventId) {
        EventRecord event = findRequired(eventId);
        event.markApplied(Instant.now());
    }

    @Transactional
    public void markFailed(
            String eventId,
            String failureReason
    ) {
        EventRecord event = findRequired(eventId);
        event.markFailed(failureReason);
    }

    private EventRecord findRequired(String eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() ->
                        new EventNotFoundException(eventId)
                );
    }
}