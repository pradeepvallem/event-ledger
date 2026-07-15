package com.eventledger.gateway.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private EventMetrics eventMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        eventMetrics = new EventMetrics(meterRegistry);
    }

    @Test
    @DisplayName("records each event outcome independently")
    void shouldRecordEventOutcomes() {
        eventMetrics.recordReceived();
        eventMetrics.recordReceived();
        eventMetrics.recordApplied();
        eventMetrics.recordFailed();
        eventMetrics.recordReplayed();
        eventMetrics.recordConflict();

        assertCounter("received", 2.0);
        assertCounter("applied", 1.0);
        assertCounter("failed", 1.0);
        assertCounter("replayed", 1.0);
        assertCounter("conflicted", 1.0);
    }

    private void assertCounter(
            String outcome,
            double expectedValue
    ) {
        double actualValue = meterRegistry
                .get("event.ledger.events")
                .tag("outcome", outcome)
                .counter()
                .count();

        assertThat(actualValue).isEqualTo(expectedValue);
    }
}