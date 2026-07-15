package com.eventledger.gateway.client;

import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.web.client.HttpClientErrorException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@CircuitBreaker(
        name = "accountService",
        fallbackMethod = "applyTransactionFallback"
)
public class AccountServiceClient implements AccountGateway {

    private final RestClient restClient;
    private final AccountServiceResponseValidator responseValidator;

    public AccountServiceClient(RestClient accountServiceRestClient, AccountServiceResponseValidator responseValidator) {
        this.restClient = accountServiceRestClient;
        this.responseValidator = responseValidator;
    }

    private static final Logger log =
            LoggerFactory.getLogger(AccountServiceClient.class);

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

        log.atInfo()
                .addKeyValue("eventId", event.getEventId())
                .addKeyValue("accountId", event.getAccountId())
                .addKeyValue(
                        "downstreamService",
                        "account-service"
                )
                .log("Calling downstream service");

        try {
            AccountTransactionResponse response =  restClient
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
            log.info(
                    "Account Service applied eventId={}",
                    event.getEventId()
            );
            responseValidator.validate(event, response);
            return response;
        } catch (AccountServiceUnavailableException exception) {
            throw exception;
        } catch (HttpClientErrorException clientErrorException) {
            throw clientErrorException;
        } catch (RestClientException exception) {
            throw new AccountServiceUnavailableException(
                    "Account Service is unavailable",
                    exception
            );
        }
    }

    private AccountTransactionResponse applyTransactionFallback(
            EventRecord event,
            Throwable throwable
    ) {
        log.atWarn()
                .addKeyValue("eventId", event.getEventId())
                .addKeyValue("accountId", event.getAccountId())
                .addKeyValue(
                        "downstreamService",
                        "account-service"
                )
                .addKeyValue(
                        "failureType",
                        throwable.getClass().getSimpleName()
                )
                .log("Circuit breaker fallback invoked");

        if (throwable instanceof AccountServiceUnavailableException exception) {
            throw exception;
        }

        throw new AccountServiceUnavailableException(
                "Account Service is temporarily unavailable",
                throwable
        );
    }
}