package com.eventledger.account.service;

import com.eventledger.account.api.dto.AccountResponse;
import com.eventledger.account.api.dto.ApplyTransactionRequest;
import com.eventledger.account.api.dto.BalanceResponse;
import com.eventledger.account.domain.Account;
import com.eventledger.account.domain.AccountTransaction;
import com.eventledger.account.domain.TransactionType;
import com.eventledger.account.exception.AccountNotFoundException;
import com.eventledger.account.exception.ConflictingEventException;
import com.eventledger.account.exception.CurrencyMismatchException;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.AccountTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String ACCOUNT_ID = "acct-123";
    private static final String EVENT_ID = "evt-001";
    private static final String CURRENCY = "USD";

    private static final Instant EVENT_TIMESTAMP =
            Instant.parse("2026-05-15T10:00:00Z");

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountTransactionRepository transactionRepository;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(
                accountRepository,
                transactionRepository
        );
    }

    @Nested
    @DisplayName("Apply transaction")
    class ApplyTransactionTests {

        @Test
        @DisplayName(
                "creates an account and transaction when account does not exist"
        )
        void shouldCreateAccountAndTransactionForNewAccount() {
            ApplyTransactionRequest request = creditRequest(
                    EVENT_ID,
                    "100.00",
                    "usd",
                    EVENT_TIMESTAMP
            );

            when(transactionRepository.findByEventId(EVENT_ID))
                    .thenReturn(Optional.empty());

            when(accountRepository.findById(ACCOUNT_ID))
                    .thenReturn(Optional.empty());

            when(accountRepository.save(any(Account.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            when(
                    transactionRepository.save(
                            any(AccountTransaction.class)
                    )
            ).thenAnswer(invocation -> invocation.getArgument(0));

            TransactionApplicationResult result =
                    accountService.applyTransaction(ACCOUNT_ID, request);

            assertThat(result.created()).isTrue();
            assertThat(result.transaction().eventId())
                    .isEqualTo(EVENT_ID);
            assertThat(result.transaction().accountId())
                    .isEqualTo(ACCOUNT_ID);
            assertThat(result.transaction().type())
                    .isEqualTo(TransactionType.CREDIT);
            assertThat(result.transaction().amount())
                    .isEqualByComparingTo("100.00");
            assertThat(result.transaction().currency())
                    .isEqualTo(CURRENCY);
            assertThat(result.transaction().eventTimestamp())
                    .isEqualTo(EVENT_TIMESTAMP);
            assertThat(result.transaction().idempotentReplay())
                    .isFalse();

            ArgumentCaptor<Account> accountCaptor =
                    ArgumentCaptor.forClass(Account.class);

            verify(accountRepository).save(accountCaptor.capture());

            Account savedAccount = accountCaptor.getValue();

            assertThat(savedAccount.getAccountId())
                    .isEqualTo(ACCOUNT_ID);
            assertThat(savedAccount.getCurrency())
                    .isEqualTo(CURRENCY);

            ArgumentCaptor<AccountTransaction> transactionCaptor =
                    ArgumentCaptor.forClass(AccountTransaction.class);

            verify(transactionRepository)
                    .save(transactionCaptor.capture());

            AccountTransaction savedTransaction =
                    transactionCaptor.getValue();

            assertThat(savedTransaction.getEventId())
                    .isEqualTo(EVENT_ID);
            assertThat(savedTransaction.getAccount())
                    .isSameAs(savedAccount);
            assertThat(savedTransaction.getType())
                    .isEqualTo(TransactionType.CREDIT);
            assertThat(savedTransaction.getAmount())
                    .isEqualByComparingTo("100.00");
            assertThat(savedTransaction.getCurrency())
                    .isEqualTo(CURRENCY);
        }

        @Test
        @DisplayName(
                "uses an existing account when its currency matches"
        )
        void shouldUseExistingAccountWhenCurrencyMatches() {
            Account existingAccount =
                    new Account(ACCOUNT_ID, CURRENCY);

            ApplyTransactionRequest request = debitRequest(
                    EVENT_ID,
                    "25.00",
                    CURRENCY,
                    EVENT_TIMESTAMP
            );

            when(transactionRepository.findByEventId(EVENT_ID))
                    .thenReturn(Optional.empty());

            when(accountRepository.findById(ACCOUNT_ID))
                    .thenReturn(Optional.of(existingAccount));

            when(
                    transactionRepository.save(
                            any(AccountTransaction.class)
                    )
            ).thenAnswer(invocation -> invocation.getArgument(0));

            TransactionApplicationResult result =
                    accountService.applyTransaction(ACCOUNT_ID, request);

            assertThat(result.created()).isTrue();
            assertThat(result.transaction().type())
                    .isEqualTo(TransactionType.DEBIT);

            verify(accountRepository, never())
                    .save(any(Account.class));

            ArgumentCaptor<AccountTransaction> captor =
                    ArgumentCaptor.forClass(AccountTransaction.class);

            verify(transactionRepository).save(captor.capture());

            assertThat(captor.getValue().getAccount())
                    .isSameAs(existingAccount);
        }

        @Test
        @DisplayName(
                "returns an idempotent replay for an identical event"
        )
        void shouldReturnIdempotentReplayForIdenticalEvent() {
            Account account = new Account(ACCOUNT_ID, CURRENCY);

            AccountTransaction existingTransaction =
                    new AccountTransaction(
                            EVENT_ID,
                            account,
                            TransactionType.CREDIT,
                            new BigDecimal("100.00"),
                            CURRENCY,
                            EVENT_TIMESTAMP
                    );

            ApplyTransactionRequest request = creditRequest(
                    EVENT_ID,
                    "100.0",
                    "usd",
                    EVENT_TIMESTAMP
            );

            when(transactionRepository.findByEventId(EVENT_ID))
                    .thenReturn(Optional.of(existingTransaction));

            TransactionApplicationResult result =
                    accountService.applyTransaction(ACCOUNT_ID, request);

            assertThat(result.created()).isFalse();
            assertThat(result.transaction().idempotentReplay())
                    .isTrue();
            assertThat(result.transaction().eventId())
                    .isEqualTo(EVENT_ID);
            assertThat(result.transaction().amount())
                    .isEqualByComparingTo("100.00");

            verify(transactionRepository, never())
                    .save(any(AccountTransaction.class));

            verifyNoInteractions(accountRepository);
        }

        @Test
        @DisplayName(
                "rejects reuse of an event ID with a different amount"
        )
        void shouldRejectDuplicateEventWithDifferentAmount() {
            Account account = new Account(ACCOUNT_ID, CURRENCY);

            AccountTransaction existingTransaction =
                    new AccountTransaction(
                            EVENT_ID,
                            account,
                            TransactionType.CREDIT,
                            new BigDecimal("100.00"),
                            CURRENCY,
                            EVENT_TIMESTAMP
                    );

            ApplyTransactionRequest conflictingRequest = creditRequest(
                    EVENT_ID,
                    "500.00",
                    CURRENCY,
                    EVENT_TIMESTAMP
            );

            when(transactionRepository.findByEventId(EVENT_ID))
                    .thenReturn(Optional.of(existingTransaction));

            assertThatThrownBy(() ->
                    accountService.applyTransaction(
                            ACCOUNT_ID,
                            conflictingRequest
                    )
            )
                    .isInstanceOf(ConflictingEventException.class)
                    .hasMessageContaining(EVENT_ID)
                    .hasMessageContaining("different transaction data");

            verify(transactionRepository, never())
                    .save(any(AccountTransaction.class));

            verifyNoInteractions(accountRepository);
        }

        @Test
        @DisplayName(
                "rejects reuse of an event ID for another account"
        )
        void shouldRejectDuplicateEventForDifferentAccount() {
            Account originalAccount =
                    new Account("acct-original", CURRENCY);

            AccountTransaction existingTransaction =
                    new AccountTransaction(
                            EVENT_ID,
                            originalAccount,
                            TransactionType.CREDIT,
                            new BigDecimal("100.00"),
                            CURRENCY,
                            EVENT_TIMESTAMP
                    );

            ApplyTransactionRequest request = creditRequest(
                    EVENT_ID,
                    "100.00",
                    CURRENCY,
                    EVENT_TIMESTAMP
            );

            when(transactionRepository.findByEventId(EVENT_ID))
                    .thenReturn(Optional.of(existingTransaction));

            assertThatThrownBy(() ->
                    accountService.applyTransaction(ACCOUNT_ID, request)
            )
                    .isInstanceOf(ConflictingEventException.class);

            verify(transactionRepository, never())
                    .save(any(AccountTransaction.class));

            verifyNoInteractions(accountRepository);
        }

        @Test
        @DisplayName(
                "rejects a transaction whose currency differs from account"
        )
        void shouldRejectCurrencyMismatch() {
            Account account = new Account(ACCOUNT_ID, CURRENCY);

            ApplyTransactionRequest request = creditRequest(
                    EVENT_ID,
                    "100.00",
                    "EUR",
                    EVENT_TIMESTAMP
            );

            when(transactionRepository.findByEventId(EVENT_ID))
                    .thenReturn(Optional.empty());

            when(accountRepository.findById(ACCOUNT_ID))
                    .thenReturn(Optional.of(account));

            assertThatThrownBy(() ->
                    accountService.applyTransaction(ACCOUNT_ID, request)
            )
                    .isInstanceOf(CurrencyMismatchException.class)
                    .hasMessageContaining("expected USD")
                    .hasMessageContaining("received EUR");

            verify(accountRepository, never())
                    .save(any(Account.class));

            verify(transactionRepository, never())
                    .save(any(AccountTransaction.class));
        }

        @Test
        @DisplayName("trims account and event identifiers")
        void shouldNormalizeIdentifiers() {
            ApplyTransactionRequest request = creditRequest(
                    "  evt-001  ",
                    "100.00",
                    "usd",
                    EVENT_TIMESTAMP
            );

            when(transactionRepository.findByEventId(EVENT_ID))
                    .thenReturn(Optional.empty());

            when(accountRepository.findById(ACCOUNT_ID))
                    .thenReturn(Optional.empty());

            when(accountRepository.save(any(Account.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            when(
                    transactionRepository.save(
                            any(AccountTransaction.class)
                    )
            ).thenAnswer(invocation -> invocation.getArgument(0));

            TransactionApplicationResult result =
                    accountService.applyTransaction(
                            "  acct-123  ",
                            request
                    );

            assertThat(result.transaction().accountId())
                    .isEqualTo(ACCOUNT_ID);
            assertThat(result.transaction().eventId())
                    .isEqualTo(EVENT_ID);
            assertThat(result.transaction().currency())
                    .isEqualTo(CURRENCY);

            verify(transactionRepository)
                    .findByEventId(EVENT_ID);

            verify(accountRepository)
                    .findById(ACCOUNT_ID);
        }

        @Test
        @DisplayName("rejects a blank account identifier")
        void shouldRejectBlankAccountId() {
            ApplyTransactionRequest request = creditRequest(
                    EVENT_ID,
                    "100.00",
                    CURRENCY,
                    EVENT_TIMESTAMP
            );

            assertThatThrownBy(() ->
                    accountService.applyTransaction("   ", request)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("accountId must not be blank");

            verifyNoInteractions(
                    accountRepository,
                    transactionRepository
            );
        }
    }

    @Nested
    @DisplayName("Get balance")
    class GetBalanceTests {

        @Test
        @DisplayName(
                "calculates balance as credits minus debits"
        )
        void shouldCalculateBalanceFromCreditsAndDebits() {
            Account account = new Account(ACCOUNT_ID, CURRENCY);

            when(accountRepository.findById(ACCOUNT_ID))
                    .thenReturn(Optional.of(account));

            when(
                    transactionRepository.sumAmountByAccountIdAndType(
                            ACCOUNT_ID,
                            TransactionType.CREDIT
                    )
            ).thenReturn(new BigDecimal("250.00"));

            when(
                    transactionRepository.sumAmountByAccountIdAndType(
                            ACCOUNT_ID,
                            TransactionType.DEBIT
                    )
            ).thenReturn(new BigDecimal("80.00"));

            BalanceResponse response =
                    accountService.getBalance(ACCOUNT_ID);

            assertThat(response.accountId())
                    .isEqualTo(ACCOUNT_ID);
            assertThat(response.currency())
                    .isEqualTo(CURRENCY);
            assertThat(response.balance())
                    .isEqualByComparingTo("170.00");

            verify(transactionRepository)
                    .sumAmountByAccountIdAndType(
                            ACCOUNT_ID,
                            TransactionType.CREDIT
                    );

            verify(transactionRepository)
                    .sumAmountByAccountIdAndType(
                            ACCOUNT_ID,
                            TransactionType.DEBIT
                    );
        }

        @Test
        @DisplayName(
                "supports a negative balance when debits exceed credits"
        )
        void shouldCalculateNegativeBalance() {
            Account account = new Account(ACCOUNT_ID, CURRENCY);

            when(accountRepository.findById(ACCOUNT_ID))
                    .thenReturn(Optional.of(account));

            when(
                    transactionRepository.sumAmountByAccountIdAndType(
                            ACCOUNT_ID,
                            TransactionType.CREDIT
                    )
            ).thenReturn(new BigDecimal("50.00"));

            when(
                    transactionRepository.sumAmountByAccountIdAndType(
                            ACCOUNT_ID,
                            TransactionType.DEBIT
                    )
            ).thenReturn(new BigDecimal("75.00"));

            BalanceResponse response =
                    accountService.getBalance(ACCOUNT_ID);

            assertThat(response.balance())
                    .isEqualByComparingTo("-25.00");
        }

        @Test
        @DisplayName(
                "throws account not found when retrieving unknown balance"
        )
        void shouldThrowWhenBalanceAccountDoesNotExist() {
            when(accountRepository.findById("acct-missing"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    accountService.getBalance("acct-missing")
            )
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessage("Account not found: acct-missing");

            verify(transactionRepository, never())
                    .sumAmountByAccountIdAndType(
                            any(String.class),
                            any(TransactionType.class)
                    );
        }
    }

    @Nested
    @DisplayName("Get account")
    class GetAccountTests {

        @Test
        @DisplayName(
                "returns account balance and chronological recent transactions"
        )
        void shouldReturnAccountDetails() {
            Account account = new Account(ACCOUNT_ID, CURRENCY);

            AccountTransaction latest = new AccountTransaction(
                    "evt-latest",
                    account,
                    TransactionType.DEBIT,
                    new BigDecimal("25.00"),
                    CURRENCY,
                    Instant.parse("2026-05-15T14:00:00Z")
            );

            AccountTransaction middle = new AccountTransaction(
                    "evt-middle",
                    account,
                    TransactionType.CREDIT,
                    new BigDecimal("50.00"),
                    CURRENCY,
                    Instant.parse("2026-05-15T11:00:00Z")
            );

            AccountTransaction earliest = new AccountTransaction(
                    "evt-earliest",
                    account,
                    TransactionType.CREDIT,
                    new BigDecimal("100.00"),
                    CURRENCY,
                    Instant.parse("2026-05-15T08:00:00Z")
            );

            when(accountRepository.findById(ACCOUNT_ID))
                    .thenReturn(Optional.of(account));

            when(
                    transactionRepository.sumAmountByAccountIdAndType(
                            ACCOUNT_ID,
                            TransactionType.CREDIT
                    )
            ).thenReturn(new BigDecimal("150.00"));

            when(
                    transactionRepository.sumAmountByAccountIdAndType(
                            ACCOUNT_ID,
                            TransactionType.DEBIT
                    )
            ).thenReturn(new BigDecimal("25.00"));

            /*
             * The repository returns newest first. AccountService reverses
             * the result to produce chronological order in the response.
             */
            when(
                    transactionRepository
                            .findByAccountAccountIdOrderByEventTimestampDescTransactionIdDesc(
                                    eq(ACCOUNT_ID),
                                    any(Pageable.class)
                            )
            ).thenReturn(
                    new java.util.ArrayList<>(
                            List.of(latest, middle, earliest)
                    )
            );

            AccountResponse response =
                    accountService.getAccount(ACCOUNT_ID);

            assertThat(response.accountId())
                    .isEqualTo(ACCOUNT_ID);
            assertThat(response.currency())
                    .isEqualTo(CURRENCY);
            assertThat(response.balance())
                    .isEqualByComparingTo("125.00");

            assertThat(response.recentTransactions())
                    .extracting(transaction -> transaction.eventId())
                    .containsExactly(
                            "evt-earliest",
                            "evt-middle",
                            "evt-latest"
                    );

            verify(transactionRepository)
                    .findByAccountAccountIdOrderByEventTimestampDescTransactionIdDesc(
                            eq(ACCOUNT_ID),
                            any(Pageable.class)
                    );
        }

        @Test
        @DisplayName(
                "requests only the configured number of recent transactions"
        )
        void shouldRequestOnlyTwentyRecentTransactions() {
            Account account = new Account(ACCOUNT_ID, CURRENCY);

            when(accountRepository.findById(ACCOUNT_ID))
                    .thenReturn(Optional.of(account));

            when(
                    transactionRepository.sumAmountByAccountIdAndType(
                            ACCOUNT_ID,
                            TransactionType.CREDIT
                    )
            ).thenReturn(BigDecimal.ZERO);

            when(
                    transactionRepository.sumAmountByAccountIdAndType(
                            ACCOUNT_ID,
                            TransactionType.DEBIT
                    )
            ).thenReturn(BigDecimal.ZERO);

            when(
                    transactionRepository
                            .findByAccountAccountIdOrderByEventTimestampDescTransactionIdDesc(
                                    eq(ACCOUNT_ID),
                                    any(Pageable.class)
                            )
            ).thenReturn(new java.util.ArrayList<>());

            accountService.getAccount(ACCOUNT_ID);

            ArgumentCaptor<Pageable> pageableCaptor =
                    ArgumentCaptor.forClass(Pageable.class);

            verify(transactionRepository)
                    .findByAccountAccountIdOrderByEventTimestampDescTransactionIdDesc(
                            eq(ACCOUNT_ID),
                            pageableCaptor.capture()
                    );

            Pageable pageable = pageableCaptor.getValue();

            assertThat(pageable.getPageNumber()).isZero();
            assertThat(pageable.getPageSize()).isEqualTo(20);
        }

        @Test
        @DisplayName(
                "throws account not found for an unknown account"
        )
        void shouldThrowWhenAccountDoesNotExist() {
            when(accountRepository.findById("acct-missing"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    accountService.getAccount("acct-missing")
            )
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessage("Account not found: acct-missing");

            verifyNoInteractions(transactionRepository);
        }
    }

    private ApplyTransactionRequest creditRequest(
            String eventId,
            String amount,
            String currency,
            Instant timestamp
    ) {
        return new ApplyTransactionRequest(
                eventId,
                TransactionType.CREDIT,
                new BigDecimal(amount),
                currency,
                timestamp
        );
    }

    private ApplyTransactionRequest debitRequest(
            String eventId,
            String amount,
            String currency,
            Instant timestamp
    ) {
        return new ApplyTransactionRequest(
                eventId,
                TransactionType.DEBIT,
                new BigDecimal(amount),
                currency,
                timestamp
        );
    }
}