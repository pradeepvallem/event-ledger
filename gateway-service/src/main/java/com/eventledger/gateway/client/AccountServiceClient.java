package com.eventledger.gateway.client;

import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AccountServiceClient implements AccountGateway {

    private final RestClient restClient;

    public AccountServiceClient(RestClient accountServiceRestClient) {
        this.restClient = accountServiceRestClient;
    }

    @Override
    public AccountTransactionResponse applyTransaction(
            EventRecord event
    ) {
        ApplyTransactionRequest request =
                new ApplyTransactionRequest(
                        event.getEventId(),
                        event.getType(),
                        event.getAmount(),
                        event.getCurrency(),
                        event.getEventTimestamp()
                );

        try {
            return restClient
                    .post()
                    .uri(
                            "/accounts/{accountId}/transactions",
                            event.getAccountId()
                    )
                    .body(request)
                    .retrieve()
                    .onStatus(
                            HttpStatusCode::is5xxServerError,
                            (httpRequest, httpResponse) -> {
                                throw new AccountServiceUnavailableException(
                                        "Account Service returned HTTP "
                                                + httpResponse.getStatusCode(),
                                        null
                                );
                            }
                    )
                    .body(AccountTransactionResponse.class);
        } catch (AccountServiceUnavailableException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new AccountServiceUnavailableException(
                    "Account Service is unavailable",
                    exception
            );
        }
    }
}