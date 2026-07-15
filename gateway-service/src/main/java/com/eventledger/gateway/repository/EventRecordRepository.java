package com.eventledger.gateway.repository;

import com.eventledger.gateway.domain.EventRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRecordRepository
        extends JpaRepository<EventRecord, String> {

    List<EventRecord>
    findByAccountIdOrderByEventTimestampAscEventIdAsc(
            String accountId
    );
}