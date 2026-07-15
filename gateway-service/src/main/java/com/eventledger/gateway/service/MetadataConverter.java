package com.eventledger.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MetadataConverter {

    private static final TypeReference<Map<String, Object>>
            METADATA_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public MetadataConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serialize(Map<String, Object> metadata) {
        Map<String, Object> normalized =
                metadata == null ? Map.of() : Map.copyOf(metadata);

        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "metadata could not be serialized",
                    exception
            );
        }
    }

    public Map<String, Object> deserialize(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }

        try {
            return Map.copyOf(
                    objectMapper.readValue(metadataJson, METADATA_TYPE)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Stored metadata could not be deserialized",
                    exception
            );
        }
    }
}