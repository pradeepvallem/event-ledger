package com.eventledger.gateway.service;

import com.eventledger.gateway.api.dto.EventResponse;

public record EventSubmissionResult(
        EventResponse event,
        boolean created
) {
}