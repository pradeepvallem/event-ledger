package com.eventledger.gateway.resilience;

import com.eventledger.gateway.client.AccountGateway;
import com.eventledger.gateway.client.AccountTransactionResponse;
import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.domain.EventStatus;
import com.eventledger.gateway.domain.TransactionType;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import com.eventledger.gateway.repository.EventRecordRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountServiceCircuitBreakerIntegrationTest {

    private static final String CIRCUIT_BREAKER_NAME =
            "accountService";

    private static final String ACCOUNT_ID = "acct-cb";

    private static final WireMockServer wireMockServer =
            new WireMockServer(
                    options()
                            .dynamicPort()
                            .globalTemplating(true)
            );

    static {
        /*
         * DynamicPropertySource runs while Spring creates the test context,
         * before JUnit's BeforeAll callback. Start WireMock here so that its
         * base URL is already available during property registration.
         */
        wireMockServer.start();
    }

    @Autowired
    private AccountGateway accountGateway;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private EventRecordRepository eventRepository;

    @Autowired
    private MockMvc mockMvc;

    private CircuitBreaker circuitBreaker;

    @DynamicPropertySource
    static void registerProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "account-service.base-url",
                wireMockServer::baseUrl
        );

        registry.add(
                "spring.http.client.factory",
                () -> "simple"
        );

        /*
         * Set deterministic test values explicitly instead of relying on
         * whichever values happen to be present in application.yml.
         */
        registry.add(
                "resilience4j.circuitbreaker.instances.accountService"
                        + ".sliding-window-type",
                () -> "COUNT_BASED"
        );

        registry.add(
                "resilience4j.circuitbreaker.instances.accountService"
                        + ".sliding-window-size",
                () -> "5"
        );

        registry.add(
                "resilience4j.circuitbreaker.instances.accountService"
                        + ".minimum-number-of-calls",
                () -> "5"
        );

        registry.add(
                "resilience4j.circuitbreaker.instances.accountService"
                        + ".failure-rate-threshold",
                () -> "50"
        );

        registry.add(
                "resilience4j.circuitbreaker.instances.accountService"
                        + ".permitted-number-of-calls-in-half-open-state",
                () -> "2"
        );

        /*
         * Tests transition the breaker manually, so they do not need to
         * sleep while waiting for OPEN → HALF_OPEN.
         */
        registry.add(
                "resilience4j.circuitbreaker.instances.accountService"
                        + ".automatic-transition-from-open-to-half-open-enabled",
                () -> "false"
        );

        registry.add(
                "resilience4j.circuitbreaker.instances.accountService"
                        + ".wait-duration-in-open-state",
                () -> "60s"
        );
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        eventRepository.deleteAll();

        circuitBreaker = circuitBreakerRegistry
                .circuitBreaker(CIRCUIT_BREAKER_NAME);

        circuitBreaker.reset();

        assertThat(circuitBreaker.getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);

        /*
         * This proves we are invoking the Spring-managed AOP proxy rather
         * than a manually instantiated AccountServiceClient.
         */
        assertThat(AopUtils.isAopProxy(accountGateway)).isTrue();

        assertThat(
                circuitBreaker.getCircuitBreakerConfig()
                        .getSlidingWindowSize()
        ).isEqualTo(5);

        assertThat(
                circuitBreaker.getCircuitBreakerConfig()
                        .getMinimumNumberOfCalls()
        ).isEqualTo(5);

        assertThat(
                circuitBreaker.getCircuitBreakerConfig()
                        .getPermittedNumberOfCallsInHalfOpenState()
        ).isEqualTo(2);
    }

    @Test
    @DisplayName(
            "opens after the configured failure threshold and short-circuits the next call"
    )
    void shouldOpenAndShortCircuitAfterFailureThreshold() {
        stubAccountServiceFailure();
        wireMockServer.resetRequests();

        for (int call = 1; call <= 5; call++) {
            EventRecord event = event("evt-cb-" + call);

            assertThatThrownBy(() ->
                    accountGateway.applyTransaction(event)
            )
                    .isInstanceOf(
                            AccountServiceUnavailableException.class
                    );
        }

        assertThat(circuitBreaker.getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        /*
         * We deliberately do not assert that five Java invocations produce
         * exactly five WireMock requests. HTTP transport behavior should not
         * make the circuit-state test brittle.
         */
        int requestsBeforeShortCircuit =
                accountServiceRequestCount();

        assertThat(requestsBeforeShortCircuit)
                .isGreaterThanOrEqualTo(5);

        assertThatThrownBy(() ->
                accountGateway.applyTransaction(
                        event("evt-cb-short-circuited")
                )
        )
                .isInstanceOf(
                        AccountServiceUnavailableException.class
                )
                .hasMessage(
                        "Account Service is temporarily unavailable"
                );

        /*
         * This is the important HTTP assertion: once OPEN, the next call
         * does not reach the downstream server.
         */
        assertThat(accountServiceRequestCount())
                .isEqualTo(requestsBeforeShortCircuit);

        assertThat(
                circuitBreaker.getMetrics()
                        .getNumberOfNotPermittedCalls()
        ).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "returns 503 and preserves local event queries while circuit is open"
    )
    void shouldReturn503AndServeLocalHistoryWhenOpen()
            throws Exception {

        circuitBreaker.transitionToOpenState();

        assertThat(circuitBreaker.getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        wireMockServer.resetRequests();

        String requestBody = """
            {
              "eventId": "evt-open-001",
              "accountId": "acct-cb",
              "type": "CREDIT",
              "amount": 25.00,
              "currency": "USD",
              "eventTimestamp": "2026-07-15T10:00:00Z",
              "metadata": {
                "source": "circuit-breaker-test"
              }
            }
            """;

        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .post("/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Account Service is temporarily unavailable"
                                )
                );

        /*
         * The OPEN circuit rejects the call before AccountServiceClient
         * sends any HTTP request.
         */
        assertThat(accountServiceRequestCount()).isZero();

        EventRecord stored = eventRepository
                .findById("evt-open-001")
                .orElseThrow();

        assertThat(stored.getStatus())
                .isEqualTo(EventStatus.FAILED);

        assertThat(stored.getLastFailureReason())
                .isEqualTo(
                        "Account Service is temporarily unavailable"
                );

        mockMvc.perform(
                        get("/events/{eventId}", "evt-open-001")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.eventId")
                                .value("evt-open-001")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("FAILED")
                )
                .andExpect(
                        jsonPath("$.failureReason")
                                .value(
                                        "Account Service is temporarily unavailable"
                                )
                );

        mockMvc.perform(
                        get("/events")
                                .queryParam("account", ACCOUNT_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(
                        jsonPath("$[0].eventId")
                                .value("evt-open-001")
                )
                .andExpect(
                        jsonPath("$[0].status")
                                .value("FAILED")
                );
    }

    @Test
    @DisplayName(
            "closes after successful half-open trial calls"
    )
    void shouldCloseAfterSuccessfulHalfOpenCalls() {
        stubAccountServiceSuccess();
        wireMockServer.resetRequests();

        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();

        assertThat(circuitBreaker.getState())
                .isEqualTo(CircuitBreaker.State.HALF_OPEN);

        AccountTransactionResponse firstResponse =
                accountGateway.applyTransaction(
                        event("evt-recovery-1")
                );

        assertThat(firstResponse).isNotNull();

        /*
         * The breaker should remain HALF_OPEN until both permitted trial
         * calls have completed.
         */
        assertThat(circuitBreaker.getState())
                .isEqualTo(CircuitBreaker.State.HALF_OPEN);

        AccountTransactionResponse secondResponse =
                accountGateway.applyTransaction(
                        event("evt-recovery-2")
                );

        assertThat(secondResponse).isNotNull();

        assertThat(circuitBreaker.getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);

        assertThat(accountServiceRequestCount())
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName(
            "reopens when half-open trial calls fail"
    )
    void shouldReopenWhenHalfOpenTrialFails() {
        stubAccountServiceFailure();
        wireMockServer.resetRequests();

        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();

        assertThat(circuitBreaker.getState())
                .isEqualTo(CircuitBreaker.State.HALF_OPEN);

        assertThatThrownBy(() ->
                accountGateway.applyTransaction(
                        event("evt-half-open-failure-1")
                )
        ).isInstanceOf(
                AccountServiceUnavailableException.class
        );

        assertThatThrownBy(() ->
                accountGateway.applyTransaction(
                        event("evt-half-open-failure-2")
                )
        ).isInstanceOf(
                AccountServiceUnavailableException.class
        );

        assertThat(circuitBreaker.getState())
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName(
            "exposes circuit breaker metrics through Actuator"
    )
    void shouldExposeCircuitBreakerMetrics()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/metrics/resilience4j.circuitbreaker.state"
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.name")
                                .value(
                                        "resilience4j.circuitbreaker.state"
                                )
                );
    }

    private void stubAccountServiceFailure() {
        wireMockServer.stubFor(
                post(
                        urlPathMatching(
                                "/accounts/.+/transactions"
                        )
                )
                        .willReturn(
                                aResponse()
                                        .withStatus(503)
                                        .withHeader(
                                                "Content-Type",
                                                "application/json"
                                        )
                                        .withBody(
                                                """
                                                {
                                                  "status": 503,
                                                  "message": "Account Service unavailable"
                                                }
                                                """
                                        )
                        )
        );
    }

    private void stubAccountServiceSuccess() {
        wireMockServer.stubFor(
                post(
                        urlPathMatching(
                                "/accounts/.+/transactions"
                        )
                )
                        .willReturn(
                                aResponse()
                                        .withStatus(201)
                                        .withHeader(
                                                "Content-Type",
                                                "application/json"
                                        )
                                        .withBody(
                                                """
                                                {
                                                  "eventId": "{{jsonPath request.body '$.eventId'}}",
                                                  "accountId": "acct-cb",
                                                  "type": "CREDIT",
                                                  "amount": 10.00,
                                                  "currency": "USD",
                                                  "eventTimestamp": "2026-07-15T10:00:00Z",
                                                  "receivedAt": "2026-07-15T10:00:01Z",
                                                  "idempotentReplay": false
                                                }
                                                """
                                        )
                        )
        );
    }

    private int accountServiceRequestCount() {
        return wireMockServer.findAll(
                postRequestedFor(
                        urlPathMatching(
                                "/accounts/.+/transactions"
                        )
                )
        ).size();
    }

    private EventRecord event(String eventId) {
        return new EventRecord(
                eventId,
                ACCOUNT_ID,
                TransactionType.CREDIT,
                new BigDecimal("10.00"),
                "USD",
                Instant.parse("2026-07-15T10:00:00Z"),
                "{}"
        );
    }
}