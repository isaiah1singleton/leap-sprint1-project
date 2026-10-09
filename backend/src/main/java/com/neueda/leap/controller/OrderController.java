package com.neueda.leap.controller;

import com.neueda.leap.ApiRoutes;
import com.neueda.leap.models.OrderResponse;
import com.neueda.leap.models.SubmitOrderRequest;
import com.neueda.leap.service.ClientService;
import com.neueda.leap.service.OrderDecisionService;
import com.neueda.leap.service.OrderSubmissionService;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(ApiRoutes.ORDERS)
public class OrderController {
    private final OrderSubmissionService submission;
    private final OrderDecisionService decisions;
    private final ClientService clients;

    public OrderController(OrderSubmissionService submission, OrderDecisionService decisions, ClientService clients) {
        this.submission = submission;
        this.decisions = decisions;
        this.clients = clients;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> submit(@RequestBody SubmitOrderRequest request, Principal principal) {
        int clientId = clientId(principal);
        OrderResponse order = submission.submit(request, clientId, principal.getName());
        return ResponseEntity.created(URI.create(ApiRoutes.ORDERS + "/" + order.orderId())).body(order);
    }

    @GetMapping
    public List<OrderResponse> list(@RequestParam Integer accountId, Principal principal) {
        return decisions.list(accountId, clientId(principal));
    }

    @GetMapping(ApiRoutes.ORDER)
    public OrderResponse get(@PathVariable Integer orderId, Principal principal) {
        return decisions.get(orderId, clientId(principal));
    }

    @PostMapping(ApiRoutes.ORDER_CANCEL)
    public OrderResponse cancel(@PathVariable Integer orderId, Principal principal) {
        return decisions.cancel(orderId, clientId(principal));
    }

    private int clientId(Principal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to place orders.");
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

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> providerError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(
                ProblemDetail.forStatusAndDetail(error.getStatusCode(), error.getReason()));
    }
}
