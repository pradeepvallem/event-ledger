package com.eventledger.gateway.service;

import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.exception.ConflictingEventException;
import org.springframework.stereotype.Component;

@Component
public class EventReplayValidator {

    private final MetadataConverter metadataConverter;

    public EventReplayValidator(
            MetadataConverter metadataConverter
    ) {
        this.metadataConverter = metadataConverter;
    }

    public void validateIdenticalReplay(
            EventRecord existing,
            NormalizedEvent request
    ) {
        boolean identical =
                existing.getAccountId().equals(request.accountId())
                        && existing.getType() == request.type()
                        && existing.getAmount()
                        .compareTo(request.amount()) == 0
                        && existing.getCurrency()
                        .equals(request.currency())
                        && existing.getEventTimestamp()
                        .equals(request.eventTimestamp())
                        && metadataConverter
                        .deserialize(existing.getMetadataJson())
                        .equals(request.metadata());

        if (!identical) {
            throw new ConflictingEventException(
                    existing.getEventId()
            );
        }
    }
}