package com.neueda.leap.controller;

import com.neueda.leap.ApiRoutes;
import com.neueda.leap.entities.Client;
import com.neueda.leap.models.*;
import com.neueda.leap.service.AccountDataService;
import com.neueda.leap.service.ClientService;
import com.neueda.leap.service.PortfolioSummaryService;
import com.neueda.leap.service.PortfolioHistoryService;
import java.security.Principal;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(ApiRoutes.ACCOUNTS)
public class AccountDataController {
    private final AccountDataService data;
    private final ClientService clientService;
    private final PortfolioSummaryService portfolio;
    private final PortfolioHistoryService history;

    public AccountDataController(AccountDataService data, ClientService clients, PortfolioSummaryService portfolio,
                                 PortfolioHistoryService history) {
        this.data = data;
        this.clientService = clients;
        this.portfolio = portfolio;
        this.history = history;
    }

    @GetMapping(ApiRoutes.ACCOUNT_PORTFOLIO_HISTORY)
    public PortfolioHistoryResponse portfolioHistory(@PathVariable Integer accountId, @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "ALL") String range) {
        return history.history(accountId, clientId(jwt), range);
    }

    @GetMapping(ApiRoutes.ACCOUNT_PORTFOLIO_SUMMARY)
    public PortfolioSummaryResponse portfolioSummary(@PathVariable Integer accountId, @AuthenticationPrincipal Jwt jwt) {
        return portfolio.summary(accountId, clientId(jwt));
    }

    @GetMapping(ApiRoutes.ACCOUNT_BALANCE)
    public AccountBalanceResponse balance(@PathVariable Integer accountId, @AuthenticationPrincipal Jwt jwt) {
        return data.balance(accountId, clientId(jwt));
    }

    @GetMapping(ApiRoutes.ACCOUNT_HOLDINGS)
    public List<HoldingResponse> holdings(@PathVariable Integer accountId, @AuthenticationPrincipal Jwt jwt) {
        return data.holdings(accountId, clientId(jwt));
    }

    @PostMapping(ApiRoutes.ACCOUNT_TRANSFERS)
    public TransferResponse transfer(@PathVariable Integer accountId, @RequestBody TransferRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        return data.transfer(accountId, clientId(jwt), request);
    }

    private int clientId(Jwt jwt) {
        if (jwt == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to access accounts.");
        return clientService.findOrRegisterClientByJwt(jwt).getClientId();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail storageUnavailable(DataAccessException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Account data storage is temporarily unavailable. Please try again later.");
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
