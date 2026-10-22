package com.neueda.leap.service;

import com.neueda.leap.entities.*;
import com.neueda.leap.enums.*;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.models.SubmitOrderRequest;
import com.neueda.leap.service.order_validation.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class OrderValidationServiceTest {
    private final OrderValidationService service = new OrderValidationService(new TradingRules(), CLOCK);
    @Test void rejectsIncompleteRequestAndNumericBoundaries() {
        List<SubmitOrderRequest> invalid = new ArrayList<>(); invalid.add(null);
        for (Integer id : Arrays.asList(null, 0, -1)) {
            invalid.add(new SubmitOrderRequest(id, 2, OrderSide.BUY, bd("1"), bd("1"), AT, "USD"));
            invalid.add(new SubmitOrderRequest(1, id, OrderSide.BUY, bd("1"), bd("1"), AT, "USD"));
        }
        for (String n : new String[]{null, "0", "-1", "0.000000001", "10000000000000000"}) {
            BigDecimal value = n == null ? null : bd(n);
            invalid.add(new SubmitOrderRequest(1, 2, OrderSide.BUY, value, bd("1"), AT, "USD"));
            invalid.add(new SubmitOrderRequest(1, 2, OrderSide.BUY, bd("1"), value, AT, "USD"));
        }
        invalid.add(new SubmitOrderRequest(1, 2, null, bd("1"), bd("1"), AT, "USD"));
        invalid.add(new SubmitOrderRequest(1, 2, OrderSide.BUY, bd("1"), bd("1"), null, "USD"));
        for (String c : new String[]{null, "", " "}) invalid.add(new SubmitOrderRequest(1, 2, OrderSide.BUY, bd("1"), bd("1"), AT, c));
        invalid.forEach(r -> assertThatIllegalArgumentException().isThrownBy(() -> service.validateSubmissionRequest(r)));
        assertThatCode(() -> service.validateSubmissionRequest(request(OrderSide.BUY))).doesNotThrowAnyException();
        assertThatCode(() -> service.validateSubmissionRequest(new SubmitOrderRequest(1, 2, OrderSide.BUY, bd("9999999999999999.99999999"), bd("1.000000000"), AT, "USD"))).doesNotThrowAnyException();
    }
    @Test void quoteCurrencyAndTimeAreCheckedButDelayIsAllowed() {
        var r = request(OrderSide.BUY);
        assertThat(service.quoteFromSubmittedRequest(r, instrument()).quoteAt()).isEqualTo(r.quoteAsOf());
        assertThatCode(() -> service.quoteFromSubmittedRequest(new SubmitOrderRequest(1, 2, OrderSide.BUY, bd("1"), bd("1"), AT, "usd"), instrument())).doesNotThrowAnyException();
        assertThatIllegalArgumentException().isThrownBy(() -> service.quoteFromSubmittedRequest(new SubmitOrderRequest(1, 2, OrderSide.BUY, bd("1"), bd("1"), AT, "EUR"), instrument()));
        assertThatIllegalArgumentException().isThrownBy(() -> service.quoteFromSubmittedRequest(new SubmitOrderRequest(1, 2, OrderSide.BUY, bd("1"), bd("1"), AT.plusNanos(1), "USD"), instrument()));
    }
    @Test void exactCashAndUnitsPassWithoutMutation() {
        Order buy = order(OrderSide.BUY), sell = order(OrderSide.SELL);
        QuoteSnapshot q = new QuoteSnapshot(usd("100"), AT.minusDays(1));
        service.validate(buy, q, "ALICE@EXAMPLE.COM", usd("200"), null);
        service.validate(sell, q, "alice@example.com", null, bd("2"));
        assertThat(buy.getRequestedQuantity()).isEqualByComparingTo("2");
        for (Money cash : Arrays.asList(null, usd("199.99"), new Money(bd("200"), Currency.EUR)))
            rejected("INSUFFICIENT_CASH", () -> service.validate(buy, q, "alice@example.com", cash, null));
        for (BigDecimal units : Arrays.asList(null, bd("1.99999999")))
            rejected("INSUFFICIENT_UNITS", () -> service.validate(sell, q, "alice@example.com", null, units));
    }
    @Test void ownershipAndAccountStatusAreIndependentOfTokenImplementation() {
        Order o = order(OrderSide.BUY); QuoteSnapshot q = new QuoteSnapshot(usd("100"), AT);
        rejected("ORDER_MISSING", () -> service.validate(null, q, "alice@example.com", usd("200"), null));
        for (String email : new String[]{null, "other@example.com"})
            rejected("ORDER_NOT_OWNED", () -> service.validate(o, q, email, usd("200"), null));
        o.getAccount().changeStatus(AccountStatus.INACTIVE);
        rejected("ACCOUNT_INACTIVE", () -> service.validate(o, q, "alice@example.com", usd("200"), null));
        o.getAccount().changeStatus(AccountStatus.ACTIVE); id(o.getAccount().getClient(), "clientStatus", ClientStatus.INACTIVE);
        rejected("ACCOUNT_INACTIVE", () -> service.validate(o, q, "alice@example.com", usd("200"), null));
    }
    @Test void instrumentPolicyAndQuantityPrecisionAreEnforced() {
        Order o = order(OrderSide.BUY); QuoteSnapshot q = new QuoteSnapshot(usd("100"), AT);
        o.getInstrument().disableTrading(); rejected("INSTRUMENT_UNAVAILABLE", () -> validate(o, q));
        o.getInstrument().enableTrading(); id(o.getInstrument(), "assetClass", null);
        rejected("ASSET_UNSUPPORTED", () -> validate(o, q)); id(o.getInstrument(), "assetClass", AssetClass.EQUITY);
        id(o.getInstrument(), "market", "UNKNOWN"); rejected("MARKET_UNSUPPORTED", () -> validate(o, q));
        id(o.getInstrument(), "market", null); rejected("INSTRUMENT_MISSING", () -> validate(o, q));
        final Order fractional = order(OrderSide.BUY); id(fractional, "requestedQuantity", bd("1.5"));
        rejected("QUANTITY_PRECISION", () -> validate(fractional, q));
        id(fractional.getInstrument(), "assetClass", AssetClass.CRYPTO);
        assertThatCode(() -> validate(fractional, q)).doesNotThrowAnyException();
    }
    @Test void invalidQuotesAndClosedMarketAreRejected() {
        Order o = order(OrderSide.BUY);
        rejected("QUOTE_MISSING", () -> validate(o, null));
        rejected("QUOTE_MISSING", () -> validate(o, new QuoteSnapshot(usd("0"), AT)));
        rejected("QUOTE_CURRENCY_MISMATCH", () -> validate(o, new QuoteSnapshot(new Money(bd("100"), Currency.EUR), AT)));
        rejected("QUOTE_TIME_INVALID", () -> validate(o, new QuoteSnapshot(usd("100"), AT.plusNanos(1))));
        var closed = new OrderValidationService(new TradingRules(), java.time.Clock.fixed(NOW.plusSeconds(86400 * 3), java.time.ZoneOffset.UTC));
        rejected("MARKET_CLOSED", () -> closed.validate(o, new QuoteSnapshot(usd("100"), AT), "alice@example.com", usd("200"), null));
        Properties p = new Properties(); p.setProperty("trading.asset.EQUITY.supported", "true"); p.setProperty("trading.instrument.2.restricted", "true");
        rejected("INSTRUMENT_UNAVAILABLE", () -> new OrderValidationService(new TradingRules(p), CLOCK).validate(o, new QuoteSnapshot(usd("100"), AT), "alice@example.com", usd("200"), null));
    }
    private void validate(Order o, QuoteSnapshot q) { service.validate(o, q, "alice@example.com", usd("200"), bd("2")); }
    @Test void defensiveChecksRejectMissingOrderFieldsAndRelationships() {
        Order o = order(OrderSide.BUY); QuoteSnapshot q = new QuoteSnapshot(usd("100"), AT);
        id(o, "side", null); rejected("SIDE_MISSING", () -> validate(o, q)); id(o, "side", OrderSide.BUY);
        for (String quantity : new String[]{null, "0", "-1"}) {
            id(o, "requestedQuantity", quantity == null ? null : bd(quantity)); rejected("QUANTITY_INVALID", () -> validate(o, q));
        }
        id(o, "requestedQuantity", bd("2")); id(o, "instrument", null); rejected("INSTRUMENT_MISSING", () -> validate(o, q));
        id(o, "instrument", instrument()); id(o.getInstrument(), "symbol", " "); rejected("INSTRUMENT_MISSING", () -> validate(o, q));
        id(o.getInstrument(), "symbol", null); rejected("INSTRUMENT_MISSING", () -> validate(o, q));
        id(o.getInstrument(), "symbol", "AAPL"); id(o.getInstrument(), "quoteCurrency", null); rejected("INSTRUMENT_MISSING", () -> validate(o, q));
        id(o, "instrument", instrument()); id(o.getAccount().getClient(), "email", null); rejected("ORDER_NOT_OWNED", () -> validate(o, q));
        id(o.getAccount(), "client", null); rejected("ACCOUNT_INACTIVE", () -> validate(o, q));
        id(o, "account", null); rejected("ACCOUNT_INACTIVE", () -> validate(o, q));
    }
    private void rejected(String code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(OrderValidationException.class)
            .satisfies(ex -> assertThat(((OrderValidationException) ex).getCode()).contains(code));
    }
}
