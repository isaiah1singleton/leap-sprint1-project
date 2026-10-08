package com.neueda.leap.controller;

import com.neueda.leap.ApiRoutes;
import com.neueda.leap.models.*;
import com.neueda.leap.service.MarketDataService;
import com.neueda.leap.service.MarketSymbolService;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(ApiRoutes.MARKET)
public class MarketController {
    private final MarketSymbolService symbols;
    private final MarketDataService market;

    public MarketController(MarketSymbolService symbols, MarketDataService market) {
        this.symbols = symbols;
        this.market = market;
    }

    @GetMapping(ApiRoutes.MARKET_SYMBOLS)
    public List<MarketSymbolDto> search(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "15") int limit) {
        return symbols.search(q, limit);
    }

    @GetMapping(ApiRoutes.MARKET_QUOTES)
    public MarketQuotesDto quotes(@RequestParam String symbols) {
        return market.quotes(symbols);
    }

    @GetMapping(ApiRoutes.MARKET_QUOTE)
    public MarketQuoteResultDto quote(@PathVariable String symbol) {
        return market.quote(symbol);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail notFound(NoSuchElementException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> providerError(ResponseStatusException exception) {
        var response = ResponseEntity.status(exception.getStatusCode());
        if (exception.getStatusCode().value() == 429 || exception.getStatusCode().value() == 503) {
            response.header("Retry-After", "60");
        }
        return response.body(ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason()));
    }
}
