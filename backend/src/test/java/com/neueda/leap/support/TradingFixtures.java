package com.neueda.leap.support;

import com.neueda.leap.entities.*;
import com.neueda.leap.enums.AssetClass;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.models.*;
import java.math.BigDecimal;
import java.time.*;
import org.springframework.test.util.ReflectionTestUtils;

/** Real domain fixtures; authentication deliberately stays outside business unit tests. */
public final class TradingFixtures {
    public static final Instant NOW = Instant.parse("2026-10-14T18:00:00Z");
    public static final OffsetDateTime AT = NOW.atOffset(ZoneOffset.UTC);
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private TradingFixtures() {}
    public static BigDecimal bd(String value) { return new BigDecimal(value); }
    public static Money usd(String value) { return new Money(bd(value), Currency.USD); }
    public static <T> T id(T entity, String field, Object value) {
        ReflectionTestUtils.setField(entity, field, value); return entity;
    }
    public static Client client() { return id(new Client("Alice", "alice@example.com", "hash"), "clientId", 7); }
    public static Account account() { return id(new Account(client(), "Trading"), "accountId", 1); }
    public static Instrument instrument() {
        Instrument i = id(new Instrument("US", "AAPL", "Apple", AssetClass.EQUITY, Currency.USD), "instrumentId", 2);
        i.enableTrading(); return i;
    }
    public static Order order(OrderSide side) { return order(account(), instrument(), side); }
    public static Order order(Account a, Instrument i, OrderSide side) {
        return id(new Order(a, i, side, bd("2"), bd("100"), AT.minusMinutes(10), AT.minusSeconds(30)), "orderId", 3);
    }
    public static OrderEvent event(Order o, OrderStatus status) {
        boolean quoted = status == OrderStatus.ACCEPTED || status == OrderStatus.FILLED;
        return id(new OrderEvent(o, status, status == OrderStatus.REJECTED ? "Rejected" : null,
            quoted ? bd("100") : null, quoted ? AT.minusMinutes(1) : null, AT.minusSeconds(20)), "orderEventId", 4);
    }
    public static Fill fill(Order o) { return id(new Fill(o, bd("101"), AT), "fillId", 5); }
    public static SubmitOrderRequest request(OrderSide side) {
        return new SubmitOrderRequest(1, 2, side, bd("2"), bd("100"), AT.minusMinutes(1), "USD");
    }
    public static MarketQuoteResultDto quote() { return quote("AAPL", "99", "101", AT.minusMinutes(10).toString()); }
    public static MarketQuoteResultDto quote(String symbol, String bid, String ask, String time) {
        return new MarketQuoteResultDto(symbol, new MarketQuoteDto(symbol, bd("100"),
            bid == null ? null : bd(bid), ask == null ? null : bd(ask), null,
            "USD", null, null, null, time, "REGULAR"), "provider", true, null);
    }
    public static TradeAuditEvent audit() { return TradeAuditEvent.from(order(OrderSide.BUY), event(order(OrderSide.BUY), OrderStatus.ACCEPTED)); }
}
