package com.eventledger.gateway.api.dto;

import java.util.Map;

public record EventMetadataResponse(
        Map<String, Object> values
) {

    public EventMetadataResponse {
        values = values == null
                ? Map.of()
                : Map.copyOf(values);
    }
}