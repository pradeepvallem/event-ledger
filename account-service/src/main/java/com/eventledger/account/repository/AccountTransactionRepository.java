package com.eventledger.account.repository;

import com.eventledger.account.domain.AccountTransaction;
import com.eventledger.account.domain.TransactionType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountTransactionRepository
        extends JpaRepository<AccountTransaction, Long> {

    Optional<AccountTransaction> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    List<AccountTransaction>
    findByAccountAccountIdOrderByEventTimestampAscTransactionIdAsc(
            String accountId
    );

    List<AccountTransaction>
    findByAccountAccountIdOrderByEventTimestampDescTransactionIdDesc(
            String accountId,
            Pageable pageable
    );

    @Query("""
        select coalesce(sum(transaction.amount), 0)
        from AccountTransaction transaction
        where transaction.account.accountId = :accountId
          and transaction.type = :type
        """)
    BigDecimal sumAmountByAccountIdAndType(
            @Param("accountId") String accountId,
            @Param("type") TransactionType type
    );
}