package com.neueda.leap.controller;

import com.neueda.leap.ApiRoutes;
import com.neueda.leap.models.OrderResponse;
import com.neueda.leap.models.SubmitOrderRequest;
import com.neueda.leap.service.ClientService;
import com.neueda.leap.service.OrderQueryService;
import com.neueda.leap.service.OrderSubmissionService;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(ApiRoutes.ORDERS)
public class OrderController {
    private final OrderSubmissionService submission;
    private final OrderQueryService queries;
    private final ClientService clients;

    public OrderController(OrderSubmissionService submission, OrderQueryService queries, ClientService clients) {
        this.submission = submission;
        this.queries = queries;
        this.clients = clients;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> submit(@RequestBody SubmitOrderRequest request, @AuthenticationPrincipal Jwt jwt) {
        int clientId = clientId(jwt);
        OrderResponse order = submission.submit(request, clientId, jwt.getClaimAsString("sub"));
        return ResponseEntity.created(URI.create(ApiRoutes.ORDERS + "/" + order.orderId())).body(order);
    }

    @GetMapping
    public List<OrderResponse> list(@RequestParam Integer accountId, @AuthenticationPrincipal Jwt jwt) {
        return queries.list(accountId, clientId(jwt));
    }

    @GetMapping(ApiRoutes.ORDER)
    public OrderResponse get(@PathVariable Integer orderId, @AuthenticationPrincipal Jwt jwt) {
        return queries.get(orderId, clientId(jwt));
    }

    private int clientId(Jwt jwt) {
        if (jwt == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to place orders.");
        return clients.findOrRegisterClientByJwt(jwt).getClientId();
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
