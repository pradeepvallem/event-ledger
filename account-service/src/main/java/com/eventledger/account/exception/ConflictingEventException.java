package com.eventledger.account.exception;

public class ConflictingEventException extends RuntimeException {

    public ConflictingEventException(String eventId) {
        super(
                "Event ID %s already exists with different transaction data"
                        .formatted(eventId)
        );
    }
}