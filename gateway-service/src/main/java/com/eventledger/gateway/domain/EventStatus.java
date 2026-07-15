package com.eventledger.gateway.domain;

public enum EventStatus {

    /**
     * Event was accepted and stored locally, but has not yet been
     * successfully applied by the Account Service.
     */
    RECEIVED,

    /**
     * Account Service successfully applied the transaction.
     */
    APPLIED,

    /**
     * Gateway could not apply the event to the Account Service.
     */
    FAILED
}