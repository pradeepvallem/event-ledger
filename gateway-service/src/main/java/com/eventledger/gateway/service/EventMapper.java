package com.eventledger.gateway.service;

import com.eventledger.gateway.api.dto.EventResponse;
import com.eventledger.gateway.domain.EventRecord;
import org.springframework.stereotype.Component;

@Component
public class EventMapper {

    private final MetadataConverter metadataConverter;

    public EventMapper(MetadataConverter metadataConverter) {
        this.metadataConverter = metadataConverter;
    }

    public EventResponse toResponse(
            EventRecord event,
            boolean idempotentReplay
    ) {
        return new EventResponse(
                event.getEventId(),
                event.getAccountId(),
                event.getType(),
                event.getAmount(),
                event.getCurrency(),
                event.getEventTimestamp(),
                metadataConverter.deserialize(event.getMetadataJson()),
                event.getStatus(),
                event.getReceivedAt(),
                event.getAppliedAt(),
                event.getLastFailureReason(),
                idempotentReplay
        );
    }
}