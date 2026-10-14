package com.neueda.leap.service.order_validation;

import com.neueda.leap.entities.Account;
import com.neueda.leap.entities.Instrument;
import com.neueda.leap.entities.Money;
import com.neueda.leap.entities.Order;
import com.neueda.leap.entities.QuoteSnapshot;
import com.neueda.leap.enums.ClientStatus;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.models.SubmitOrderRequest;
import com.neueda.leap.service.TradingRules;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class OrderValidationService {
    private final TradingRules rules;
    private final Clock clock;

    public OrderValidationService(TradingRules rules, Clock clock) {
        this.rules = rules;
        this.clock = clock;
    }

    /** Invalid request or quote data never creates an order record. */
    public void validateSubmissionRequest(SubmitOrderRequest request) {
        if (request == null
                || request.accountId() == null || request.accountId() <= 0
                || request.instrumentId() == null || request.instrumentId() <= 0
                || request.side() == null
                || !fitsOrderNumeric(request.quantity())
                || !fitsOrderNumeric(request.indicativePrice())
                || request.quoteAsOf() == null
                || request.quoteCurrency() == null || request.quoteCurrency().isBlank()) {
            throw new IllegalArgumentException("A valid account, instrument, BUY or SELL side, "
                    + "positive quantity, and indicative quote are required.");
        }
    }

    public QuoteSnapshot quoteFromSubmittedRequest(SubmitOrderRequest request, Instrument instrument) {
        if (!instrument.getQuoteCurrency().name().equalsIgnoreCase(request.quoteCurrency())) {
            throw new IllegalArgumentException("The quote currency does not match the instrument.");
        }
        if (request.quoteAsOf().toInstant().isAfter(clock.instant())) {
            throw new IllegalArgumentException("The quote timestamp cannot be in the future.");
        }
        return new QuoteSnapshot(new Money(request.indicativePrice(), instrument.getQuoteCurrency()),
                request.quoteAsOf());
    }

    /**
     * Validates a submitted order against total cash and unreserved sell quantity.
     * This method does not look up, save, accept, or reserve an order.
     */
    public void validate(Order order, QuoteSnapshot submittedQuote, String authenticatedEmail,
                         Money totalCash, BigDecimal availableUnits) {
        validateSubmittedOrder(order, authenticatedEmail);
        Instrument instrument = order.getInstrument();
        Instant now = validateInstrument(instrument);
        OrderSide side = order.getSide();
        BigDecimal quantity = order.getRequestedQuantity();
        validateOrderFields(side, quantity, instrument);
        validateQuote(submittedQuote, instrument, now);
        validateResources(side, quantity, submittedQuote, instrument, totalCash, availableUnits);
    }

    private static void validateSubmittedOrder(Order order, String authenticatedEmail) {
        if (order == null) {
            throw failure(OrderValidationErrorCodes.ORDER_MISSING, "A submitted order is required.");
        }

        Account account = order.getAccount();
        if (account == null || account.getClient() == null || !account.isActive()
                || account.getClient().getClientStatus() != ClientStatus.ACTIVE) {
            throw failure(OrderValidationErrorCodes.ACCOUNT_INACTIVE, "The account or client is not active.");
        }
        if (authenticatedEmail == null || account.getClient().getEmail() == null
                || !account.getClient().getEmail().equalsIgnoreCase(authenticatedEmail)) {
            throw failure(OrderValidationErrorCodes.ORDER_NOT_OWNED, "The order does not belong to the authenticated client.");
        }
    }

    private Instant validateInstrument(Instrument instrument) {
        if (instrument == null || instrument.getMarket() == null || instrument.getSymbol() == null
                || instrument.getSymbol().isBlank() || instrument.getQuoteCurrency() == null) {
            throw failure(OrderValidationErrorCodes.INSTRUMENT_MISSING, "The instrument is not recognized.");
        }
        if (!rules.supports(instrument.getAssetClass())) {
            throw failure(OrderValidationErrorCodes.ASSET_UNSUPPORTED, "The asset class is not supported for trading.");
        }
        if (!instrument.isTradable() || rules.isRestricted(instrument)) {
            throw failure(OrderValidationErrorCodes.INSTRUMENT_UNAVAILABLE, "The instrument is not enabled for trading.");
        }
        Instant now = clock.instant();
        var marketWindow = rules.marketWindow(instrument.getMarket());
        if (marketWindow.isEmpty()) {
            throw failure(OrderValidationErrorCodes.MARKET_UNSUPPORTED, "The market has no trading policy.");
        }
        if (!marketWindow.orElseThrow().isOpen(now)) {
            throw failure(OrderValidationErrorCodes.MARKET_CLOSED, "The market is closed under the configured trading hours.");
        }
        return now;
    }

    private void validateOrderFields(OrderSide side, BigDecimal quantity, Instrument instrument) {
        if (side == null) {
            throw failure(OrderValidationErrorCodes.SIDE_MISSING, "A BUY or SELL side is required.");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw failure(OrderValidationErrorCodes.QUANTITY_INVALID, "Quantity must be positive.");
        }
        if (Math.max(0, quantity.stripTrailingZeros().scale()) > rules.allowedQuantityScale(instrument)) {
            throw failure(OrderValidationErrorCodes.QUANTITY_PRECISION, "Quantity exceeds the configured precision.");
        }
    }

    private void validateQuote(QuoteSnapshot submittedQuote, Instrument instrument, Instant now) {
        if (submittedQuote == null || submittedQuote.price() == null
                || submittedQuote.price().amount() == null || submittedQuote.price().amount().signum() <= 0
                || submittedQuote.quoteAt() == null) {
            throw failure(OrderValidationErrorCodes.QUOTE_MISSING, "A positive submitted price and quote time are required.");
        }
        if (submittedQuote.price().currency() != instrument.getQuoteCurrency()) {
            throw failure(OrderValidationErrorCodes.QUOTE_CURRENCY_MISMATCH, "The quote currency does not match the instrument.");
        }
        Instant quotedAt = submittedQuote.quoteAt().toInstant();
        if (quotedAt.isAfter(now)) {
            throw failure(OrderValidationErrorCodes.QUOTE_TIME_INVALID, "The quote timestamp cannot be in the future.");
        }
    }

    private static boolean fitsOrderNumeric(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            return false;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return Math.max(0, normalized.scale()) <= 8
                && Math.max(0, normalized.precision() - normalized.scale()) <= 16;
    }

    private void validateResources(OrderSide side, BigDecimal quantity, QuoteSnapshot submittedQuote,
                                   Instrument instrument, Money totalCash, BigDecimal availableUnits) {
        if (side == OrderSide.BUY) {
            BigDecimal requiredCash = submittedQuote.price().amount().multiply(quantity);
            if (totalCash == null || totalCash.amount() == null
                    || totalCash.currency() != instrument.getQuoteCurrency()
                    || totalCash.amount().compareTo(requiredCash) < 0) {
                throw failure(OrderValidationErrorCodes.INSUFFICIENT_CASH, "Cash balance does not cover the order.");
            }
        } else if (side == OrderSide.SELL) {
            if (availableUnits == null || availableUnits.compareTo(quantity) < 0) {
                throw failure(OrderValidationErrorCodes.INSUFFICIENT_UNITS, "Available units do not cover the sale.");
            }
        }
    }

    private static OrderValidationException failure(String code, String message) {
        return new OrderValidationException(code, message);
    }
}
