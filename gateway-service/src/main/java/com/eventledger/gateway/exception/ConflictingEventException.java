package com.eventledger.gateway.exception;

public class ConflictingEventException extends RuntimeException {

    public ConflictingEventException(String eventId) {
        super(
                "Event ID %s already exists with different event data"
                        .formatted(eventId)
        );
    }
}