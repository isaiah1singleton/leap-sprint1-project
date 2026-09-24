package com.neueda.leap.controller;

import com.neueda.leap.models.AccountResponse;
import com.neueda.leap.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.security.Principal;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    @Autowired
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> openAccount(Principal principal) {
        Integer clientId = authenticatedClientId(principal);

        AccountResponse account = accountService.openAccount(clientId);

        URI location = URI.create("/api/accounts/" + account.accountId());

        return ResponseEntity.created(location).body(account);
    }

    @GetMapping("/{accountId}")
    public AccountResponse getAccount(@PathVariable("accountId") Integer accountId, Principal principal) {
        Integer clientId = authenticatedClientId(principal);
        return accountService.getAccount(accountId, clientId);
    }

    private Integer authenticatedClientId(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to access accounts.");
        }
        return accountService
                .findActiveClientIdByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "An active client login is required."));
    }
    // 404 NOT FOUND
    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(NoSuchElementException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // 409 CONFLICT
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleInvalidState(IllegalStateException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }
}
