package com.eventledger.gateway.exception;

import com.eventledger.gateway.domain.EventStatus;

public class EventNotAppliedException extends RuntimeException {

    private final String eventId;
    private final EventStatus eventStatus;

    public EventNotAppliedException(
            String eventId,
            EventStatus eventStatus,
            String failureReason
    ) {
        super(buildMessage(eventId, eventStatus, failureReason));
        this.eventId = eventId;
        this.eventStatus = eventStatus;
    }

    private static String buildMessage(
            String eventId,
            EventStatus eventStatus,
            String failureReason
    ) {
        if (failureReason != null && !failureReason.isBlank()) {
            return "Event %s was not applied: %s"
                    .formatted(eventId, failureReason);
        }

        return "Event %s is currently in status %s"
                .formatted(eventId, eventStatus);
    }

    public String getEventId() {
        return eventId;
    }

    public EventStatus getEventStatus() {
        return eventStatus;
    }
}