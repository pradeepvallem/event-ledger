package com.eventledger.gateway.repository;

import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.domain.EventStatus;
import com.eventledger.gateway.domain.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class EventRecordRepositoryTest {

    private static final String ACCOUNT_ID = "acct-123";

    @Autowired
    private EventRecordRepository eventRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName(
            "orders account events by event timestamp, not arrival order"
    )
    void shouldOrderEventsChronologically() {
        save(
                "evt-latest",
                "2026-05-15T14:00:00Z"
        );

        save(
                "evt-earliest",
                "2026-05-15T08:00:00Z"
        );

        save(
                "evt-middle",
                "2026-05-15T11:00:00Z"
        );

        var events =
                eventRepository
                        .findByAccountIdOrderByEventTimestampAscEventIdAsc(
                                ACCOUNT_ID
                        );

        assertThat(events)
                .extracting(EventRecord::getEventId)
                .containsExactly(
                        "evt-earliest",
                        "evt-middle",
                        "evt-latest"
                );
    }

    @Test
    @DisplayName(
            "uses event ID as a database-enforced unique key"
    )
    void shouldRejectDuplicateEventId() {
        EventRecord original = new EventRecord(
                "evt-duplicate",
                ACCOUNT_ID,
                TransactionType.CREDIT,
                new BigDecimal("100.00"),
                "USD",
                Instant.parse("2026-05-15T08:00:00Z"),
                "{}"
        );

        entityManager.persist(original);
        entityManager.flush();
        entityManager.clear();

        EventRecord duplicate = new EventRecord(
                "evt-duplicate",
                "acct-other",
                TransactionType.DEBIT,
                new BigDecimal("25.00"),
                "USD",
                Instant.parse("2026-05-15T09:00:00Z"),
                "{}"
        );

        assertThatThrownBy(() -> {
            entityManager.persist(duplicate);
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName(
            "initializes received time and RECEIVED status during persistence"
    )
    void shouldInitializePersistenceFields() {
        EventRecord saved = save(
                "evt-001",
                "2026-05-15T08:00:00Z"
        );

        assertThat(saved.getReceivedAt()).isNotNull();
        assertThat(saved.getStatus())
                .isEqualTo(EventStatus.RECEIVED);
    }

    @Test
    @DisplayName("persists applied state")
    void shouldPersistAppliedState() {
        EventRecord event = save(
                "evt-applied",
                "2026-05-15T08:00:00Z"
        );

        Instant appliedAt =
                Instant.parse("2026-07-15T07:00:00Z");

        event.markApplied(appliedAt);
        eventRepository.saveAndFlush(event);

        EventRecord reloaded =
                eventRepository.findById("evt-applied")
                        .orElseThrow();

        assertThat(reloaded.getStatus())
                .isEqualTo(EventStatus.APPLIED);
        assertThat(reloaded.getAppliedAt())
                .isEqualTo(appliedAt);
        assertThat(reloaded.getLastFailureReason())
                .isNull();
    }

    @Test
    @DisplayName("persists failed state")
    void shouldPersistFailedState() {
        EventRecord event = save(
                "evt-failed",
                "2026-05-15T08:00:00Z"
        );

        event.markFailed("Account Service unavailable");
        eventRepository.saveAndFlush(event);

        EventRecord reloaded =
                eventRepository.findById("evt-failed")
                        .orElseThrow();

        assertThat(reloaded.getStatus())
                .isEqualTo(EventStatus.FAILED);
        assertThat(reloaded.getLastFailureReason())
                .isEqualTo("Account Service unavailable");
    }

    private EventRecord save(
            String eventId,
            String timestamp
    ) {
        return eventRepository.saveAndFlush(
                new EventRecord(
                        eventId,
                        ACCOUNT_ID,
                        TransactionType.CREDIT,
                        new BigDecimal("100.00"),
                        "USD",
                        Instant.parse(timestamp),
                        "{}"
                )
        );
    }
}