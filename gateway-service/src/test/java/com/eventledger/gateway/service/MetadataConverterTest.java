package com.eventledger.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetadataConverterTest {

    private MetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new MetadataConverter(new ObjectMapper());
    }

    @Test
    @DisplayName("should serialize metadata")
    void shouldSerializeMetadata() {

        Map<String, Object> metadata = Map.of(
                "source", "batch",
                "batchId", "B-001"
        );

        String json = converter.serialize(metadata);

        assertTrue(json.contains("source"));
        assertTrue(json.contains("batch"));
        assertTrue(json.contains("batchId"));
    }

    @Test
    @DisplayName("should deserialize metadata")
    void shouldDeserializeMetadata() {

        String json = """
            {
              "source":"batch",
              "batchId":"B-001"
            }
            """;

        Map<String, Object> metadata =
                converter.deserialize(json);

        assertEquals("batch", metadata.get("source"));
        assertEquals("B-001", metadata.get("batchId"));
    }

    @Test
    @DisplayName("null metadata should serialize as empty JSON")
    void shouldHandleNullMetadata() {

        String json = converter.serialize(null);

        assertEquals("{}", json);
    }

    @Test
    @DisplayName("null JSON should deserialize to empty map")
    void shouldHandleNullJson() {

        assertTrue(
                converter.deserialize(null).isEmpty()
        );
    }

    @Test
    @DisplayName("invalid JSON should throw exception")
    void shouldThrowForInvalidJson() {

        assertThrows(
                IllegalStateException.class,
                () -> converter.deserialize("{")
        );
    }
}