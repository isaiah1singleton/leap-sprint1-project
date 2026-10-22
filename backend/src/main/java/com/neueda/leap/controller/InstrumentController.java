package com.neueda.leap.controller;

import com.neueda.leap.models.InstrumentResponse;
import com.neueda.leap.service.ClientService;
import com.neueda.leap.service.InstrumentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/instruments")
public class InstrumentController {

    private final InstrumentService instrumentService;
    private final ClientService clientService;

    public InstrumentController(
            InstrumentService instrumentService,
            ClientService clientService
    ) {
        this.instrumentService = instrumentService;
        this.clientService = clientService;
    }

    @GetMapping
    public List<InstrumentResponse> getInstruments(
            @AuthenticationPrincipal Jwt jwt
    ) {
        requireActiveClient(jwt);

        return instrumentService.getInstruments();
    }

    @GetMapping("/{instrumentId}")
    public InstrumentResponse getInstrument(
            @PathVariable("instrumentId") Integer instrumentId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        requireActiveClient(jwt);

        return instrumentService.getInstrument(instrumentId);
    }

    private void requireActiveClient(Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Sign in to access instruments."
            );
        }

        clientService.findOrRegisterClientByJwt(jwt);
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