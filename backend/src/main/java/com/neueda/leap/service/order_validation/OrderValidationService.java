package com.neueda.leap.service.order_validation;

import com.neueda.leap.entities.Account;
import com.neueda.leap.entities.Instrument;
import com.neueda.leap.entities.Money;
import com.neueda.leap.entities.Order;
import com.neueda.leap.entities.QuoteSnapshot;
import com.neueda.leap.enums.ClientStatus;
import com.neueda.leap.enums.OrderSide;
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

    /**
     * Validates an already submitted order and its available resource snapshot.
     * The caller supplies available amounts after pending reservations have been deducted.
     * This method does not look up, save, accept, or reserve an order.
     */
    public void validate(Order order, QuoteSnapshot submittedQuote, String authenticatedEmail,
                         Money availableCash, BigDecimal availableUnits) {
        validateSubmittedOrder(order, authenticatedEmail);
        Instrument instrument = order.getInstrument();
        Instant now = validateInstrument(instrument);
        OrderSide side = order.getSide();
        BigDecimal quantity = order.getRequestedQuantity();
        validateOrderFields(side, quantity, instrument);
        validateQuote(submittedQuote, instrument, now);
        validateAvailableResources(side, quantity, submittedQuote, instrument, availableCash, availableUnits);
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
        if (quotedAt.isAfter(now) || quotedAt.isBefore(now.minusSeconds(rules.maxQuoteAgeSeconds()))) {
            throw failure(OrderValidationErrorCodes.QUOTE_STALE, "The submitted quote is outside the allowed age.");
        }
    }

    private void validateAvailableResources(OrderSide side, BigDecimal quantity, QuoteSnapshot submittedQuote,
                                            Instrument instrument, Money availableCash, BigDecimal availableUnits) {
        if (side == OrderSide.BUY) {
            BigDecimal requiredCash = submittedQuote.price().amount().multiply(quantity)
                    .multiply(rules.fundingMultiplier(instrument.getAssetClass()));
            if (availableCash == null || availableCash.amount() == null
                    || availableCash.currency() != instrument.getQuoteCurrency()
                    || availableCash.amount().compareTo(requiredCash) < 0) {
                throw failure(OrderValidationErrorCodes.INSUFFICIENT_CASH, "Available cash does not cover the order and configured charges.");
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
