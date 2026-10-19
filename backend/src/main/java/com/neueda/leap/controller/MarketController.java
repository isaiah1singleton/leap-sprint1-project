
package com.neueda.leap.controller;

import com.neueda.leap.ApiRoutes;
import com.neueda.leap.models.MarketQuoteResultDto;
import com.neueda.leap.models.MarketQuotesDto;
import com.neueda.leap.models.MarketSymbolDto;
import com.neueda.leap.service.MarketDataService;
import com.neueda.leap.service.MarketSymbolService;
import com.neueda.leap.market.QuoteProviderException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;

import static com.neueda.leap.market.QuoteProviderException.Kind.NOT_FOUND;

@RestController
@RequestMapping(ApiRoutes.MARKET)
public class MarketController {

    private final MarketSymbolService symbols;
    private final MarketDataService market;
    private final Clock clock;

    public MarketController(
            MarketSymbolService symbols,
            MarketDataService market,
            Clock clock
    ) {
        this.symbols = symbols;
        this.market = market;
        this.clock = clock;
    }

    @GetMapping(ApiRoutes.MARKET_SYMBOLS)
    public List<MarketSymbolDto> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "15") int limit
    ) {
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
    public ProblemDetail badRequest(IllegalArgumentException failure) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                failure.getMessage()
        );
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail notFound(NoSuchElementException failure) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                failure.getMessage()
        );
    }

    @ExceptionHandler(QuoteProviderException.class)
    public ResponseEntity<ProblemDetail> providerError(
            QuoteProviderException failure
    ) {
        HttpStatus status = switch (failure.kind()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case CONFIGURATION, UNAVAILABLE ->
                    HttpStatus.SERVICE_UNAVAILABLE;
            case INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
        };

        var response = ResponseEntity.status(status);

        if (failure.retryAt() != null) {
            Duration remaining = Duration.between(
                    clock.instant(),
                    failure.retryAt()
            );

            long seconds = remaining.isNegative() || remaining.isZero()
                    ? 0
                    : remaining.getSeconds()
                    + (remaining.getNano() == 0 ? 0 : 1);

            response.header(
                    HttpHeaders.RETRY_AFTER,
                    Long.toString(seconds)
            );
        }

        return response.body(
                ProblemDetail.forStatusAndDetail(
                        status,
                        failure.getMessage()
                )
        );
    }
}
