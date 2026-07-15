package com.eventledger.gateway.api;

import com.eventledger.gateway.client.AccountGateway;
import com.eventledger.gateway.client.AccountTransactionResponse;
import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.domain.EventStatus;
import com.eventledger.gateway.domain.TransactionType;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import com.eventledger.gateway.repository.EventRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasItem;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRecordRepository eventRepository;

    @MockitoBean
    private AccountGateway accountGateway;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        reset(accountGateway);
    }

    @Test
    @DisplayName(
            "accepts, stores and applies a valid event"
    )
    void shouldSubmitNewEvent() throws Exception {
        when(accountGateway.applyTransaction(any()))
                .thenReturn(accountResponse("evt-001"));

        mockMvc.perform(
                        post("/events")
                                .contentType("application/json")
                                .content(validEventJson("evt-001"))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value("evt-001"))
                .andExpect(jsonPath("$.accountId").value("acct-123"))
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(
                        jsonPath("$.idempotentReplay").value(false)
                )
                .andExpect(
                        jsonPath("$.metadata.source")
                                .value("mainframe-batch")
                );

        EventRecord stored =
                eventRepository.findById("evt-001")
                        .orElseThrow();

        assertThat(stored.getStatus())
                .isEqualTo(EventStatus.APPLIED);
        assertThat(stored.getAppliedAt()).isNotNull();

        verify(accountGateway)
                .applyTransaction(any(EventRecord.class));
    }

    @Test
    @DisplayName(
            "returns the original event for an identical replay"
    )
    void shouldHandleIdenticalReplay() throws Exception {
        when(accountGateway.applyTransaction(any()))
                .thenReturn(accountResponse("evt-duplicate"));

        String body = validEventJson("evt-duplicate");

        mockMvc.perform(
                        post("/events")
                                .contentType("application/json")
                                .content(body)
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.idempotentReplay").value(false)
                );

        mockMvc.perform(
                        post("/events")
                                .contentType("application/json")
                                .content(body)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.idempotentReplay").value(true)
                )
                .andExpect(jsonPath("$.status").value("APPLIED"));

        assertThat(eventRepository.count()).isEqualTo(1);

        verify(
                accountGateway,
                times(1)
        ).applyTransaction(any(EventRecord.class));
    }

    @Test
    @DisplayName(
            "rejects reuse of an event ID with different data"
    )
    void shouldRejectConflictingReplay() throws Exception {
        when(accountGateway.applyTransaction(any()))
                .thenReturn(accountResponse("evt-conflict"));

        mockMvc.perform(
                        post("/events")
                                .contentType("application/json")
                                .content(validEventJson("evt-conflict"))
                )
                .andExpect(status().isCreated());

        String conflictingBody = """
            {
              "eventId": "evt-conflict",
              "accountId": "acct-123",
              "type": "DEBIT",
              "amount": 999.00,
              "currency": "USD",
              "eventTimestamp": "2026-05-15T14:02:11Z",
              "metadata": {
                "source": "mainframe-batch"
              }
            }
            """;

        mockMvc.perform(
                        post("/events")
                                .contentType("application/json")
                                .content(conflictingBody)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        containsString(
                                                "different event data"
                                        )
                                )
                );

        assertThat(eventRepository.count()).isEqualTo(1);

        verify(
                accountGateway,
                times(1)
        ).applyTransaction(any(EventRecord.class));
    }

    @Test
    @DisplayName(
            "returns events in chronological order"
    )
    void shouldListEventsChronologically() throws Exception {
        when(accountGateway.applyTransaction(any()))
                .thenAnswer(invocation -> {
                    EventRecord event = invocation.getArgument(0);
                    return accountResponse(event.getEventId());
                });

        submit(
                eventJson(
                        "evt-latest",
                        "2026-05-15T14:00:00Z"
                )
        );

        submit(
                eventJson(
                        "evt-earliest",
                        "2026-05-15T08:00:00Z"
                )
        );

        submit(
                eventJson(
                        "evt-middle",
                        "2026-05-15T11:00:00Z"
                )
        );

        mockMvc.perform(
                        get("/events")
                                .queryParam("account", "acct-123")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(
                        jsonPath("$[0].eventId")
                                .value("evt-earliest")
                )
                .andExpect(
                        jsonPath("$[1].eventId")
                                .value("evt-middle")
                )
                .andExpect(
                        jsonPath("$[2].eventId")
                                .value("evt-latest")
                );
    }

    @Test
    @DisplayName(
            "stores failed event and returns 503 when Account Service is unavailable"
    )
    void shouldStoreFailedEventWhenAccountServiceUnavailable()
            throws Exception {

        when(accountGateway.applyTransaction(any()))
                .thenThrow(
                        new AccountServiceUnavailableException(
                                "Account Service is unavailable",
                                new RuntimeException("connection refused")
                        )
                );

        mockMvc.perform(
                        post("/events")
                                .contentType("application/json")
                                .content(validEventJson("evt-offline"))
                )
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(
                        jsonPath("$.message")
                                .value("Account Service is unavailable")
                );

        EventRecord stored =
                eventRepository.findById("evt-offline")
                        .orElseThrow();

        assertThat(stored.getStatus())
                .isEqualTo(EventStatus.FAILED);
        assertThat(stored.getLastFailureReason())
                .isEqualTo("Account Service is unavailable");

        mockMvc.perform(
                        get("/events/{eventId}", "evt-offline")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(
                        jsonPath("$.failureReason")
                                .value("Account Service is unavailable")
                );
    }

    @Test
    @DisplayName("rejects invalid event input")
    void shouldRejectInvalidInput() throws Exception {
        String invalidBody = """
            {
              "eventId": "",
              "accountId": "acct-123",
              "type": "CREDIT",
              "amount": 0,
              "currency": "US",
              "eventTimestamp": "2026-05-15T14:02:11Z"
            }
            """;

        mockMvc.perform(
                        post("/events")
                                .contentType("application/json")
                                .content(invalidBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.validationErrors.eventId")
                                .value("eventId is required")
                )
                .andExpect(
                        jsonPath("$.validationErrors.amount")
                                .value("amount must be greater than zero")
                )
                .andExpect(
                        jsonPath("$.validationErrors.currency")
                                .value(
                                        "currency must be a three-letter code"
                                )
                );

        assertThat(eventRepository.count()).isZero();
    }

    @Test
    @DisplayName("returns 404 for an unknown event")
    void shouldReturnNotFound() throws Exception {
        mockMvc.perform(
                        get("/events/{eventId}", "evt-missing")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(
                        jsonPath("$.message")
                                .value("Event not found: evt-missing")
                );
    }

    @Test
    @DisplayName("exposes Gateway health with database status")
    void shouldExposeGatewayHealth() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(
                        jsonPath("$.components.db.status").value("UP")
                );
    }

    @Test
    @DisplayName("exposes custom Gateway event metrics")
    void shouldExposeCustomEventMetrics() throws Exception {
        when(accountGateway.applyTransaction(any()))
                .thenReturn(accountResponse("evt-metric"));

        mockMvc.perform(
                        post("/events")
                                .contentType("application/json")
                                .content(validEventJson("evt-metric"))
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        get("/metrics/event.ledger.events")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.name")
                                .value("event.ledger.events")
                )
                .andExpect(
                        jsonPath("$.availableTags[*].tag")
                                .value(hasItem("outcome"))
                )
                .andExpect(
                        jsonPath("$.availableTags[*].tag")
                                .value(hasItem("application"))
                );
    }

    @Test
    @DisplayName(
            "returns 503 when replaying an event that previously failed"
    )
    void shouldReturnServiceUnavailableForFailedReplay()
            throws Exception {

        when(accountGateway.applyTransaction(any()))
                .thenThrow(
                        new AccountServiceUnavailableException(
                                "Account Service is unavailable",
                                new RuntimeException("connection refused")
                        )
                );

        String body = validEventJson("evt-failed-replay");

        mockMvc.perform(
                        post("/events")
                                .contentType("application/json")
                                .content(body)
                )
                .andExpect(status().isServiceUnavailable());

        /*
         * The second request must not call Account Service again.
         * It returns the status of the stored failed event.
         */
        mockMvc.perform(
                        post("/events")
                                .contentType("application/json")
                                .content(body)
                )
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(
                        jsonPath("$.validationErrors.eventId")
                                .value("evt-failed-replay")
                )
                .andExpect(
                        jsonPath("$.validationErrors.eventStatus")
                                .value("FAILED")
                );

        mockMvc.perform(
                        get(
                                "/events/{eventId}",
                                "evt-failed-replay"
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.eventId")
                                .value("evt-failed-replay")
                )
                .andExpect(jsonPath("$.status").value("FAILED"));

        mockMvc.perform(
                        get("/events")
                                .queryParam("account", "acct-123")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("FAILED"));

        verify(
                accountGateway,
                times(1)
        ).applyTransaction(any(EventRecord.class));

        assertThat(eventRepository.count()).isEqualTo(1);
    }

    private void submit(String body) throws Exception {
        mockMvc.perform(
                        post("/events")
                                .contentType("application/json")
                                .content(body)
                )
                .andExpect(status().isCreated());
    }

    private String validEventJson(String eventId) {
        return eventJson(
                eventId,
                "2026-05-15T14:02:11Z"
        );
    }

    private String eventJson(
            String eventId,
            String timestamp
    ) {
        return """
            {
              "eventId": "%s",
              "accountId": "acct-123",
              "type": "CREDIT",
              "amount": 150.00,
              "currency": "USD",
              "eventTimestamp": "%s",
              "metadata": {
                "source": "mainframe-batch"
              }
            }
            """.formatted(eventId, timestamp);
    }

    private AccountTransactionResponse accountResponse(
            String eventId
    ) {
        return new AccountTransactionResponse(
                eventId,
                "acct-123",
                TransactionType.CREDIT,
                new BigDecimal("150.00"),
                "USD",
                Instant.parse("2026-05-15T14:02:11Z"),
                Instant.parse("2026-07-15T07:00:00Z"),
                false
        );
    }
}