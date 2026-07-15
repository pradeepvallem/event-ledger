package com.eventledger.gateway.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class EventMetrics {

    private static final String METRIC_NAME =
            "event.ledger.events";

    private final Counter receivedCounter;
    private final Counter appliedCounter;
    private final Counter failedCounter;
    private final Counter replayedCounter;
    private final Counter conflictedCounter;

    public EventMetrics(MeterRegistry meterRegistry) {
        this.receivedCounter = Counter.builder(METRIC_NAME)
                .description("Number of events processed by the Gateway")
                .tag("outcome", "received")
                .register(meterRegistry);

        this.appliedCounter = Counter.builder(METRIC_NAME)
                .description("Number of events processed by the Gateway")
                .tag("outcome", "applied")
                .register(meterRegistry);

        this.failedCounter = Counter.builder(METRIC_NAME)
                .description("Number of events processed by the Gateway")
                .tag("outcome", "failed")
                .register(meterRegistry);

        this.replayedCounter = Counter.builder(METRIC_NAME)
                .description("Number of events processed by the Gateway")
                .tag("outcome", "replayed")
                .register(meterRegistry);

        this.conflictedCounter = Counter.builder(METRIC_NAME)
                .description("Number of events processed by the Gateway")
                .tag("outcome", "conflicted")
                .register(meterRegistry);
    }

    public void recordReceived() {
        receivedCounter.increment();
    }

    public void recordApplied() {
        appliedCounter.increment();
    }

    public void recordFailed() {
        failedCounter.increment();
    }

    public void recordReplayed() {
        replayedCounter.increment();
    }

    public void recordConflict() {
        conflictedCounter.increment();
    }
}