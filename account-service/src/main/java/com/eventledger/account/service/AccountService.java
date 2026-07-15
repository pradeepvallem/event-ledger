package com.eventledger.account.service;

import com.eventledger.account.api.dto.AccountResponse;
import com.eventledger.account.api.dto.ApplyTransactionRequest;
import com.eventledger.account.api.dto.BalanceResponse;
import com.eventledger.account.api.dto.TransactionResponse;
import com.eventledger.account.domain.Account;
import com.eventledger.account.domain.AccountTransaction;
import com.eventledger.account.domain.TransactionType;
import com.eventledger.account.exception.AccountNotFoundException;
import com.eventledger.account.exception.ConflictingEventException;
import com.eventledger.account.exception.CurrencyMismatchException;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.AccountTransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Service
public class AccountService {

    private static final int RECENT_TRANSACTION_LIMIT = 20;

    private final AccountRepository accountRepository;
    private final AccountTransactionRepository transactionRepository;

    private static final Logger log =
            LoggerFactory.getLogger(AccountService.class);

    public AccountService(
            AccountRepository accountRepository,
            AccountTransactionRepository transactionRepository
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public TransactionApplicationResult applyTransaction(
            String accountId,
            ApplyTransactionRequest request
    ) {
        log.atInfo()
                .addKeyValue("eventId", request.eventId())
                .addKeyValue("accountId", accountId)
                .addKeyValue("transactionType", request.type())
                .log("Applying transaction");

        String normalizedAccountId = normalizeRequired(accountId, "accountId");
        String normalizedEventId = normalizeRequired(
                request.eventId(),
                "eventId"
        );

        String normalizedCurrency = request.currency()
                .trim()
                .toUpperCase(Locale.ROOT);

        var existingTransaction =
                transactionRepository.findByEventId(normalizedEventId);

        if (existingTransaction.isPresent()) {
            AccountTransaction existing = existingTransaction.get();

            validateIdenticalReplay(
                    existing,
                    normalizedAccountId,
                    request,
                    normalizedCurrency
            );

            log.atInfo()
                    .addKeyValue("eventId", normalizedEventId)
                    .addKeyValue("accountId", normalizedAccountId)
                    .addKeyValue("idempotentReplay", true)
                    .log("Returning existing transaction");

            return new TransactionApplicationResult(
                    toTransactionResponse(existing, true),
                    false
            );
        }

        Account account = accountRepository
                .findById(normalizedAccountId)
                .map(existingAccount -> {
                    validateCurrency(
                            existingAccount,
                            normalizedCurrency
                    );

                    return existingAccount;
                })
                .orElseGet(() ->
                        accountRepository.save(
                                new Account(
                                        normalizedAccountId,
                                        normalizedCurrency
                                )
                        )
                );

        AccountTransaction transaction = new AccountTransaction(
                normalizedEventId,
                account,
                request.type(),
                request.amount(),
                normalizedCurrency,
                request.eventTimestamp()
        );

        AccountTransaction savedTransaction =
                transactionRepository.save(transaction);

        log.atInfo()
                .addKeyValue("eventId", savedTransaction.getEventId())
                .addKeyValue("accountId", account.getAccountId())
                .addKeyValue("transactionType", savedTransaction.getType())
                .log("Transaction persisted");

        return new TransactionApplicationResult(
                toTransactionResponse(savedTransaction, false),
                true
        );
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String accountId) {
        Account account = getExistingAccount(accountId);

        BigDecimal balance = calculateBalance(account.getAccountId());

        return new BalanceResponse(
                account.getAccountId(),
                account.getCurrency(),
                balance
        );
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(String accountId) {
        Account account = getExistingAccount(accountId);

        BigDecimal balance = calculateBalance(account.getAccountId());

        List<AccountTransaction> transactions =
                transactionRepository
                        .findByAccountAccountIdOrderByEventTimestampDescTransactionIdDesc(
                                account.getAccountId(),
                                PageRequest.of(0, RECENT_TRANSACTION_LIMIT)
                        );



        List<TransactionResponse> transactionResponses =
                transactions.reversed().stream()
                        .map(transaction ->
                                toTransactionResponse(transaction, false)
                        )
                        .toList();

        return new AccountResponse(
                account.getAccountId(),
                account.getCurrency(),
                balance,
                account.getCreatedAt(),
                transactionResponses
        );
    }

    private Account getExistingAccount(String accountId) {
        String normalizedAccountId =
                normalizeRequired(accountId, "accountId");

        return accountRepository.findById(normalizedAccountId)
                .orElseThrow(() ->
                        new AccountNotFoundException(normalizedAccountId)
                );
    }

    private BigDecimal calculateBalance(String accountId) {
        BigDecimal credits =
                transactionRepository.sumAmountByAccountIdAndType(
                        accountId,
                        TransactionType.CREDIT
                );

        BigDecimal debits =
                transactionRepository.sumAmountByAccountIdAndType(
                        accountId,
                        TransactionType.DEBIT
                );

        return credits.subtract(debits);
    }

    private void validateCurrency(
            Account account,
            String providedCurrency
    ) {
        if (!account.getCurrency().equals(providedCurrency)) {
            throw new CurrencyMismatchException(
                    account.getAccountId(),
                    account.getCurrency(),
                    providedCurrency
            );
        }
    }

    private void validateIdenticalReplay(
            AccountTransaction existing,
            String accountId,
            ApplyTransactionRequest request,
            String currency
    ) {
        boolean identical =
                existing.getAccount().getAccountId().equals(accountId)
                        && existing.getType() == request.type()
                        && existing.getAmount()
                        .compareTo(request.amount()) == 0
                        && existing.getCurrency().equals(currency)
                        && existing.getEventTimestamp()
                        .equals(request.eventTimestamp());

        if (!identical) {
            throw new ConflictingEventException(existing.getEventId());
        }
    }

    private TransactionResponse toTransactionResponse(
            AccountTransaction transaction,
            boolean idempotentReplay
    ) {
        return new TransactionResponse(
                transaction.getEventId(),
                transaction.getAccount().getAccountId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getEventTimestamp(),
                transaction.getReceivedAt(),
                idempotentReplay
        );
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        return value.trim();
    }
}