package com.eventledger.gateway.client;

import com.eventledger.gateway.domain.EventRecord;

public interface AccountGateway {

    AccountTransactionResponse applyTransaction(
            EventRecord event
    );
}