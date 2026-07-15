package com.eventledger.account.repository;

import com.eventledger.account.domain.Account;
import com.eventledger.account.domain.AccountTransaction;
import com.eventledger.account.domain.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class AccountTransactionRepositoryTest {

    private static final String ACCOUNT_ID = "acct-123";

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountTransactionRepository transactionRepository;

    private Account account;

    @BeforeEach
    void setUp() {
        account = accountRepository.save(
                new Account(ACCOUNT_ID, "USD")
        );
    }

    @Test
    @DisplayName(
            "returns transactions ordered by event timestamp rather than arrival order"
    )
    void shouldOrderTransactionsChronologically() {
        saveTransaction(
                "evt-latest",
                TransactionType.DEBIT,
                "30.00",
                "2026-05-15T12:00:00Z"
        );

        saveTransaction(
                "evt-earliest",
                TransactionType.CREDIT,
                "100.00",
                "2026-05-15T09:00:00Z"
        );

        saveTransaction(
                "evt-middle",
                TransactionType.CREDIT,
                "50.00",
                "2026-05-15T11:00:00Z"
        );

        var transactions =
                transactionRepository
                        .findByAccountAccountIdOrderByEventTimestampAscTransactionIdAsc(
                                ACCOUNT_ID
                        );

        assertThat(transactions)
                .extracting(AccountTransaction::getEventId)
                .containsExactly(
                        "evt-earliest",
                        "evt-middle",
                        "evt-latest"
                );
    }

    @Test
    @DisplayName("sums credits and debits independently")
    void shouldSumTransactionsByType() {
        saveTransaction(
                "evt-credit-1",
                TransactionType.CREDIT,
                "100.00",
                "2026-05-15T09:00:00Z"
        );

        saveTransaction(
                "evt-credit-2",
                TransactionType.CREDIT,
                "50.00",
                "2026-05-15T10:00:00Z"
        );

        saveTransaction(
                "evt-debit",
                TransactionType.DEBIT,
                "30.00",
                "2026-05-15T11:00:00Z"
        );

        BigDecimal credits =
                transactionRepository.sumAmountByAccountIdAndType(
                        ACCOUNT_ID,
                        TransactionType.CREDIT
                );

        BigDecimal debits =
                transactionRepository.sumAmountByAccountIdAndType(
                        ACCOUNT_ID,
                        TransactionType.DEBIT
                );

        assertThat(credits).isEqualByComparingTo("150.00");
        assertThat(debits).isEqualByComparingTo("30.00");
        assertThat(credits.subtract(debits))
                .isEqualByComparingTo("120.00");
    }

    @Test
    @DisplayName("returns zero when an account has no matching transaction type")
    void shouldReturnZeroWhenNoTransactionsMatch() {
        BigDecimal credits =
                transactionRepository.sumAmountByAccountIdAndType(
                        ACCOUNT_ID,
                        TransactionType.CREDIT
                );

        assertThat(credits).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("enforces event ID uniqueness at the database level")
    void shouldRejectDuplicateEventId() {
        saveTransaction(
                "evt-duplicate",
                TransactionType.CREDIT,
                "100.00",
                "2026-05-15T09:00:00Z"
        );

        AccountTransaction duplicate = new AccountTransaction(
                "evt-duplicate",
                account,
                TransactionType.DEBIT,
                new BigDecimal("25.00"),
                "USD",
                Instant.parse("2026-05-15T10:00:00Z")
        );

        assertThatThrownBy(() ->
                transactionRepository.saveAndFlush(duplicate)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void saveTransaction(
            String eventId,
            TransactionType type,
            String amount,
            String timestamp
    ) {
        transactionRepository.saveAndFlush(
                new AccountTransaction(
                        eventId,
                        account,
                        type,
                        new BigDecimal(amount),
                        "USD",
                        Instant.parse(timestamp)
                )
        );
    }
}