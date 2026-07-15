package com.eventledger.gateway.client;

import com.eventledger.gateway.domain.EventRecord;
import org.springframework.stereotype.Component;

@Component
public class AccountServiceResponseValidator {

    public void validate(
            EventRecord event,
            AccountTransactionResponse response
    ) {
        if (response == null) {
            throw new IllegalStateException(
                    "Account Service returned an empty response"
            );
        }

        boolean matches =
                event.getEventId().equals(response.eventId())
                        && event.getAccountId().equals(response.accountId())
                        && event.getType() == response.type()
                        && event.getAmount()
                        .compareTo(response.amount()) == 0
                        && event.getCurrency()
                        .equals(response.currency())
                        && event.getEventTimestamp()
                        .equals(response.eventTimestamp());

        if (!matches) {
            throw new IllegalStateException(
                    "Account Service response does not match submitted event "
                            + event.getEventId()
            );
        }
    }
}