package com.eventledger.account.api;

import com.eventledger.account.api.dto.AccountResponse;
import com.eventledger.account.api.dto.ApplyTransactionRequest;
import com.eventledger.account.api.dto.BalanceResponse;
import com.eventledger.account.api.dto.TransactionResponse;
import com.eventledger.account.service.AccountService;
import com.eventledger.account.service.TransactionApplicationResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
@Validated
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<TransactionResponse> applyTransaction(
            @PathVariable
            @NotBlank(message = "accountId is required")
            String accountId,

            @Valid
            @RequestBody
            ApplyTransactionRequest request
    ) {
        TransactionApplicationResult result =
                accountService.applyTransaction(accountId, request);

        HttpStatus status = result.created()
                ? HttpStatus.CREATED
                : HttpStatus.OK;

        return ResponseEntity
                .status(status)
                .body(result.transaction());
    }

    @GetMapping("/{accountId}/balance")
    public BalanceResponse getBalance(
            @PathVariable
            @NotBlank(message = "accountId is required")
            String accountId
    ) {
        return accountService.getBalance(accountId);
    }

    @GetMapping("/{accountId}")
    public AccountResponse getAccount(
            @PathVariable
            @NotBlank(message = "accountId is required")
            String accountId
    ) {
        return accountService.getAccount(accountId);
    }
}