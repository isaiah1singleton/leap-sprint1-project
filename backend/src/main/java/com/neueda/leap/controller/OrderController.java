package com.neueda.leap.controller;


import com.neueda.leap.ApiRoutes;
import com.neueda.leap.models.OrderResponse;
import com.neueda.leap.models.SubmitOrderRequest;
import com.neueda.leap.service.AccountService;
import com.neueda.leap.service.ClientService;
import com.neueda.leap.service.OrderSubmissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@RestController
@RequestMapping(ApiRoutes.ORDER)
public class OrderController {

    private final AccountService accountService;
    private final ClientService clientService;
    private final OrderSubmissionService orderSubmissionService;

    @Autowired
    public OrderController(
            AccountService accountService,
            ClientService clientService,
            OrderSubmissionService orderSubmissionService
    ) {
        this.accountService = accountService;
        this.clientService = clientService;
        this.orderSubmissionService = orderSubmissionService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> submitOrder
            (Principal principal, @RequestBody SubmitOrderRequest request, UserDetails authenticatedPrincipal) {
        // Integer clientId = authenticatedClientId(principal);
        // To-do: need to check that account is actually owned by client

        OrderResponse order = orderSubmissionService.submitOrder(
                    request.accountId(),
                    request.instrumentId(),
                    request.side(),
                    request.requestedQuantity(),
                    request.submittedQuotePrice()
                );

        // to-do: map to order page
        // URI location = URI.create('')
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }


    // to-do: duplicate code from account controller, extract later :/
    private Integer authenticatedClientId(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to access accounts.");
        }
        return clientService
                .findActiveClientIdByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "An active client login is required."));
    }
}
