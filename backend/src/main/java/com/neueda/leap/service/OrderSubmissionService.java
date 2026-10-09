package com.neueda.leap.service;

import com.neueda.leap.entities.Instrument;
import com.neueda.leap.entities.Money;
import com.neueda.leap.entities.QuoteSnapshot;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.models.MarketQuoteDto;
import com.neueda.leap.models.MarketQuoteResultDto;
import com.neueda.leap.models.OrderResponse;
import com.neueda.leap.models.SubmitOrderRequest;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.InstrumentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderSubmissionService {
    private final AccountRepository accounts;
    private final InstrumentRepository instruments;
    private final MarketDataService market;
    private final OrderDecisionService decisions;
    private final Clock clock;

    public OrderSubmissionService(AccountRepository accounts, InstrumentRepository instruments,
            MarketDataService market, OrderDecisionService decisions, Clock clock) {
        this.accounts = accounts;
        this.instruments = instruments;
        this.market = market;
        this.decisions = decisions;
        this.clock = clock;
    }

    public OrderResponse submit(SubmitOrderRequest request, Integer clientId, String email) {
        if (request == null || request.accountId() == null || request.accountId() <= 0
                || request.instrumentId() == null || request.instrumentId() <= 0
                || request.side() == null || request.quantity() == null
                || request.quantity().signum() <= 0 || !fitsOrderNumeric(request.quantity())) {
            throw new IllegalArgumentException("A valid account, instrument, BUY or SELL side, and positive quantity are required.");
        }
        accounts.findByAccountIdAndClient_ClientId(request.accountId(), clientId)
                .orElseThrow(() -> new NoSuchElementException("Account not found."));
        Instrument instrument = instruments.findById(request.instrumentId())
                .orElseThrow(() -> new NoSuchElementException("Instrument not found."));
        // Provider and request failures happen before any order is persisted.
        MarketQuoteResultDto result = market.quote(instrument.getSymbol());
        MarketQuoteDto quote = result.quote();
        if (result.stale() || result.error() != null || quote == null
                || !instrument.getSymbol().equalsIgnoreCase(result.symbol())
                || !instrument.getSymbol().equalsIgnoreCase(quote.symbol())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "A usable market quote is unavailable.");
        }
        if (quote.currency() != null && !instrument.getQuoteCurrency().name().equalsIgnoreCase(quote.currency())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Provider quote currency differs from the instrument.");
        }
        BigDecimal price = request.side() == OrderSide.BUY ? quote.ask() : quote.bid();
        if (price == null || price.signum() <= 0 || !fitsOrderNumeric(price)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Provider did not supply a valid side price.");
        }
        OffsetDateTime asOf;
        try {
            asOf = OffsetDateTime.parse(quote.asOf());
        } catch (DateTimeParseException | NullPointerException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Provider quote timestamp is invalid.");
        }
        if (asOf.toInstant().isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Provider quote timestamp is in the future.");
        }
        QuoteSnapshot snapshot = new QuoteSnapshot(new Money(price, instrument.getQuoteCurrency()), asOf);
        return decisions.decide(request, clientId, email, snapshot);
    }

    private static boolean fitsOrderNumeric(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        return Math.max(0, normalized.scale()) <= 8
                && Math.max(0, normalized.precision() - normalized.scale()) <= 16;
    }
}
