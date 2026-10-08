package com.neueda.leap.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.util.NoSuchElementException;


import java.security.Principal;
import org.springframework.web.server.ResponseStatusException;
import com.neueda.leap.models.FillResponse;
import com.neueda.leap.service.FillService;
import com.neueda.leap.service.ClientService;

@RestController
@RequestMapping("/api/orders")
public class FillController {
    private final FillService fillService;
    private final ClientService clientService;

    private Integer authenticatedClientId(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to access fills.");
        }
        return clientService.findActiveClientIdByEmail(principal.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "An active client login is required."));
    }

    public FillController(FillService fillService, ClientService clientService) {
        this.fillService = fillService;
        this.clientService = clientService;
    }
    @GetMapping("{orderId}/fill")
    public FillResponse getFill(
        @PathVariable("orderId") Integer orderId,
        Principal principal
    ) {
        Integer clientId = authenticatedClientId(principal);
        return fillService.getFillFromOrder(orderId, clientId);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(
        NoSuchElementException exception
    ) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(
        IllegalArgumentException exception
    ) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }   
}

