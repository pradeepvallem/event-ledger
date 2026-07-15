package com.eventledger.account.api;

import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.AccountTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountControllerIntegrationTest {

    private static final String ACCOUNT_ID = "acct-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountTransactionRepository transactionRepository;

    @BeforeEach
    void cleanDatabase() {
        /*
         * Transactions must be deleted before accounts because the
         * transaction table has a foreign key to the account table.
         */
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName(
            "calculates the correct balance when transactions arrive out of order"
    )
    void shouldCalculateCorrectBalanceWhenEventsArriveOutOfOrder()
            throws Exception {

        /*
         * Arrival order:
         *
         * 1. 12:00 DEBIT 30
         * 2. 09:00 CREDIT 100
         * 3. 11:00 CREDIT 50
         *
         * Chronological order:
         *
         * 09:00 CREDIT 100
         * 11:00 CREDIT 50
         * 12:00 DEBIT 30
         *
         * Expected balance: 100 + 50 - 30 = 120
         */

        submitTransaction(
                """
                {
                  "eventId": "evt-003",
                  "type": "DEBIT",
                  "amount": 30.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T12:00:00Z"
                }
                """,
                201
        );

        submitTransaction(
                """
                {
                  "eventId": "evt-001",
                  "type": "CREDIT",
                  "amount": 100.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T09:00:00Z"
                }
                """,
                201
        );

        submitTransaction(
                """
                {
                  "eventId": "evt-002",
                  "type": "CREDIT",
                  "amount": 50.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T11:00:00Z"
                }
                """,
                201
        );

        mockMvc.perform(
                        get("/accounts/{accountId}/balance", ACCOUNT_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.balance").value(120.0));

        assertThat(transactionRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName(
            "returns account transactions ordered by event timestamp"
    )
    void shouldReturnTransactionsInChronologicalOrder()
            throws Exception {

        submitTransaction(
                """
                {
                  "eventId": "evt-latest",
                  "type": "DEBIT",
                  "amount": 25.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:00:00Z"
                }
                """,
                201
        );

        submitTransaction(
                """
                {
                  "eventId": "evt-earliest",
                  "type": "CREDIT",
                  "amount": 100.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T08:00:00Z"
                }
                """,
                201
        );

        submitTransaction(
                """
                {
                  "eventId": "evt-middle",
                  "type": "CREDIT",
                  "amount": 50.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T11:00:00Z"
                }
                """,
                201
        );

        mockMvc.perform(
                        get("/accounts/{accountId}", ACCOUNT_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.balance").value(125.0))
                .andExpect(
                        jsonPath("$.recentTransactions.length()").value(3)
                )
                .andExpect(
                        jsonPath("$.recentTransactions[0].eventId")
                                .value("evt-earliest")
                )
                .andExpect(
                        jsonPath("$.recentTransactions[1].eventId")
                                .value("evt-middle")
                )
                .andExpect(
                        jsonPath("$.recentTransactions[2].eventId")
                                .value("evt-latest")
                )
                .andExpect(
                        jsonPath("$.recentTransactions[0].eventTimestamp")
                                .value("2026-05-15T08:00:00Z")
                )
                .andExpect(
                        jsonPath("$.recentTransactions[2].eventTimestamp")
                                .value("2026-05-15T14:00:00Z")
                );
    }

    @Test
    @DisplayName(
            "returns the original transaction for an identical duplicate event"
    )
    void shouldHandleIdenticalEventAsIdempotentReplay()
            throws Exception {

        String requestBody = """
            {
              "eventId": "evt-duplicate",
              "type": "CREDIT",
              "amount": 150.00,
              "currency": "USD",
              "eventTimestamp": "2026-05-15T14:02:11Z"
            }
            """;

        submitTransaction(requestBody, 201)
                .andExpect(
                        jsonPath("$.idempotentReplay").value(false)
                );

        submitTransaction(requestBody, 200)
                .andExpect(
                        jsonPath("$.idempotentReplay").value(true)
                )
                .andExpect(
                        jsonPath("$.eventId").value("evt-duplicate")
                )
                .andExpect(
                        jsonPath("$.amount").value(150.0)
                );

        mockMvc.perform(
                        get("/accounts/{accountId}/balance", ACCOUNT_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(150.0));

        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(accountRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "rejects reuse of an event ID with different transaction data"
    )
    void shouldRejectConflictingDuplicateEvent()
            throws Exception {

        submitTransaction(
                """
                {
                  "eventId": "evt-conflict",
                  "type": "CREDIT",
                  "amount": 100.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T10:00:00Z"
                }
                """,
                201
        );

        mockMvc.perform(
                        post(
                                "/accounts/{accountId}/transactions",
                                ACCOUNT_ID
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "eventId": "evt-conflict",
                                          "type": "DEBIT",
                                          "amount": 500.00,
                                          "currency": "USD",
                                          "eventTimestamp": "2026-05-15T10:00:00Z"
                                        }
                                        """
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        containsString(
                                                "already exists with different transaction data"
                                        )
                                )
                );

        assertThat(transactionRepository.count()).isEqualTo(1);

        mockMvc.perform(
                        get("/accounts/{accountId}/balance", ACCOUNT_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.0));
    }

    @Test
    @DisplayName("rejects zero transaction amounts")
    void shouldRejectZeroAmount() throws Exception {

        mockMvc.perform(
                        post(
                                "/accounts/{accountId}/transactions",
                                ACCOUNT_ID
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "eventId": "evt-zero",
                                          "type": "CREDIT",
                                          "amount": 0,
                                          "currency": "USD",
                                          "eventTimestamp": "2026-05-15T10:00:00Z"
                                        }
                                        """
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.message")
                                .value("Request validation failed")
                )
                .andExpect(
                        jsonPath("$.validationErrors.amount")
                                .value("amount must be greater than zero")
                );

        assertThat(transactionRepository.count()).isZero();
        assertThat(accountRepository.count()).isZero();
    }

    @Test
    @DisplayName("rejects negative transaction amounts")
    void shouldRejectNegativeAmount() throws Exception {

        mockMvc.perform(
                        post(
                                "/accounts/{accountId}/transactions",
                                ACCOUNT_ID
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "eventId": "evt-negative",
                                          "type": "DEBIT",
                                          "amount": -10.00,
                                          "currency": "USD",
                                          "eventTimestamp": "2026-05-15T10:00:00Z"
                                        }
                                        """
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.validationErrors.amount")
                                .value("amount must be greater than zero")
                );

        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    @DisplayName("rejects an unsupported transaction type")
    void shouldRejectUnsupportedTransactionType()
            throws Exception {

        mockMvc.perform(
                        post(
                                "/accounts/{accountId}/transactions",
                                ACCOUNT_ID
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "eventId": "evt-invalid-type",
                                          "type": "TRANSFER",
                                          "amount": 100.00,
                                          "currency": "USD",
                                          "eventTimestamp": "2026-05-15T10:00:00Z"
                                        }
                                        """
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Request body is malformed or contains an unsupported value"
                                )
                );

        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    @DisplayName("rejects a transaction with a missing event ID")
    void shouldRejectMissingEventId() throws Exception {

        mockMvc.perform(
                        post(
                                "/accounts/{accountId}/transactions",
                                ACCOUNT_ID
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "type": "CREDIT",
                                          "amount": 100.00,
                                          "currency": "USD",
                                          "eventTimestamp": "2026-05-15T10:00:00Z"
                                        }
                                        """
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.validationErrors.eventId")
                                .value("eventId is required")
                );

        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    @DisplayName("rejects a currency mismatch for an existing account")
    void shouldRejectCurrencyMismatch() throws Exception {

        submitTransaction(
                """
                {
                  "eventId": "evt-usd",
                  "type": "CREDIT",
                  "amount": 100.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T10:00:00Z"
                }
                """,
                201
        );

        mockMvc.perform(
                        post(
                                "/accounts/{accountId}/transactions",
                                ACCOUNT_ID
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "eventId": "evt-eur",
                                          "type": "CREDIT",
                                          "amount": 50.00,
                                          "currency": "EUR",
                                          "eventTimestamp": "2026-05-15T11:00:00Z"
                                        }
                                        """
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        containsString(
                                                "expected USD but received EUR"
                                        )
                                )
                );

        assertThat(transactionRepository.count()).isEqualTo(1);

        mockMvc.perform(
                        get("/accounts/{accountId}/balance", ACCOUNT_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.balance").value(100.0));
    }

    @Test
    @DisplayName("returns 404 when an account does not exist")
    void shouldReturnNotFoundForUnknownAccount()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/accounts/{accountId}/balance",
                                "acct-missing"
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(
                        jsonPath("$.message")
                                .value("Account not found: acct-missing")
                );
    }

    @Test
    @DisplayName("exposes Account Service health with database status")
    void shouldExposeAccountServiceHealth() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(
                        jsonPath("$.components.db.status").value("UP")
                );
    }

    private org.springframework.test.web.servlet.ResultActions
    submitTransaction(
            String requestBody,
            int expectedStatus
    ) throws Exception {

        return mockMvc.perform(
                        post(
                                "/accounts/{accountId}/transactions",
                                ACCOUNT_ID
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().is(expectedStatus));
    }
}