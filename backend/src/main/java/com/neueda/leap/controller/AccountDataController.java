package com.neueda.leap.controller;

import com.neueda.leap.ApiRoutes;
import com.neueda.leap.models.*;
import com.neueda.leap.service.AccountDataService;
import com.neueda.leap.service.ClientService;
import com.neueda.leap.service.PortfolioSummaryService;
import java.security.Principal;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(ApiRoutes.ACCOUNTS)
public class AccountDataController {
    private final AccountDataService data;
    private final ClientService clients;
    private final PortfolioSummaryService portfolio;

    public AccountDataController(AccountDataService data, ClientService clients, PortfolioSummaryService portfolio) {
        this.data = data;
        this.clients = clients;
        this.portfolio = portfolio;
    }

    @GetMapping(ApiRoutes.ACCOUNT_PORTFOLIO_SUMMARY)
    public PortfolioSummaryResponse portfolioSummary(@PathVariable Integer accountId, Principal principal) {
        return portfolio.summary(accountId, clientId(principal));
    }

    @GetMapping(ApiRoutes.ACCOUNT_BALANCE)
    public AccountBalanceResponse balance(@PathVariable Integer accountId, Principal principal) {
        return data.balance(accountId, clientId(principal));
    }

    @GetMapping(ApiRoutes.ACCOUNT_HOLDINGS)
    public List<HoldingResponse> holdings(@PathVariable Integer accountId, Principal principal) {
        return data.holdings(accountId, clientId(principal));
    }

    @PostMapping(ApiRoutes.ACCOUNT_TRANSFERS)
    public TransferResponse transfer(@PathVariable Integer accountId, @RequestBody TransferRequest request,
                                     Principal principal) {
        return data.transfer(accountId, clientId(principal), request);
    }

    private int clientId(Principal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to access accounts.");
        return clients.findActiveClientIdByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "An active client login is required."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail notFound(NoSuchElementException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail conflict(IllegalStateException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.getMessage());
    }
}
