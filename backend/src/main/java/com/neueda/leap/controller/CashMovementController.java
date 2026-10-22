package com.neueda.leap.controller;

import com.neueda.leap.models.CashMovementResponse;
import com.neueda.leap.service.CashMovementService;
import com.neueda.leap.service.ClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api")
public class CashMovementController {

    private final CashMovementService movementService;
    private final ClientService clientService;

    public CashMovementController(
            CashMovementService movementService,
            ClientService clientService
    ) {
        this.movementService = movementService;
        this.clientService = clientService;
    }

    @GetMapping("/accounts/{accountId}/cash-movements")
    public List<CashMovementResponse> getMovements(
            @PathVariable("accountId") Integer accountId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Integer clientId = authenticatedClientId(jwt);

        return movementService.getMovements(accountId, clientId);
    }

    @GetMapping("/cash-movements/{cashMovementId}")
    public CashMovementResponse getMovement(
            @PathVariable("cashMovementId") Integer movementId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Integer clientId = authenticatedClientId(jwt);

        return movementService.getMovement(movementId, clientId);
    }

    private Integer authenticatedClientId(Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Sign in to access cash movements."
            );
        }

        return clientService
                .findOrRegisterClientByJwt(jwt).getClientId();
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(
            NoSuchElementException exception
    ) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(
            IllegalArgumentException exception
    ) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );
    }
}
