package com.neueda.leap.controller;

import com.neueda.leap.ApiRoutes;
import com.neueda.leap.entities.Order;
import com.neueda.leap.entities.QuoteSnapshot;
import com.neueda.leap.service.StubSubmittedOrderSource;
import com.neueda.leap.service.order_validation.OrderValidationException;
import com.neueda.leap.service.order_validation.OrderValidationService;
import java.security.Principal;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// STUB: THIS WHOLE CONTROLLER IS PROBABLY MEANT TO BE TEMPORARY UNTIL
// WE ORGANIZE SUBMIT / VALIDATE / EXECUTE FLOW
@RestController
public class OrderValidationController {
    private final OrderValidationService validationService;
    private final StubSubmittedOrderSource stubOrders;

    public OrderValidationController(OrderValidationService validationService,
                                     StubSubmittedOrderSource stubOrders) {
        this.validationService = validationService;
        this.stubOrders = stubOrders;
    }

    @PostMapping(ApiRoutes.ORDER_VALIDATE)
    public ResponseEntity<Void> validate(@PathVariable("orderId") Integer orderId,
                                         @RequestBody(required = false) QuoteSnapshot submittedQuote,
                                         Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to validate an order.");
        }
        // STUB: the future submitted-order source will supply this Order.
        Order order = stubOrders.getSubmittedOrder(orderId, principal.getName());
        validationService.validate(order, submittedQuote, principal.getName(),
                stubOrders.availableCash(order), stubOrders.availableUnits(order));
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(OrderValidationException.class)
    public ProblemDetail handleValidationFailure(OrderValidationException error) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, error.getMessage());
        detail.setProperty("code", error.getCode());
        return detail;
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(NoSuchElementException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.getMessage());
    }
}
