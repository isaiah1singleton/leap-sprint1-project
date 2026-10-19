package com.neueda.leap.controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.neueda.leap.service.OrderEventService;
import com.neueda.leap.service.ClientService;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ProblemDetail;
import java.util.NoSuchElementException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.neueda.leap.models.OrderEventResponse;

@RestController
@RequestMapping("/api/orders")
public class OrderEventController {

    private final OrderEventService orderEventService;
    private final ClientService clientService;

    public OrderEventController(OrderEventService orderEventService, ClientService clientService) {
        this.orderEventService = orderEventService;
        this.clientService = clientService;
    }

    @GetMapping("/{orderId}/events")
    public List<OrderEventResponse> getOrderEvents(@PathVariable Integer orderId, Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to access order history.");
        }
        
        Integer clientId = clientService.findActiveClientIdByEmail(principal.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "An active client login is required."));

        return orderEventService.getEvents(orderId, clientId);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(NoSuchElementException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
}
