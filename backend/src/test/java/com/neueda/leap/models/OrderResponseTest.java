package com.neueda.leap.models;

import com.neueda.leap.entities.Instrument;
import com.neueda.leap.entities.Order;
import com.neueda.leap.entities.OrderEvent;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderResponseTest {
    @Test
    void filledOrderExposesTheExecutionPriceAndPendingOrderDoesNot() {
        Order order = mock(Order.class);
        Instrument instrument = mock(Instrument.class);
        OrderEvent event = mock(OrderEvent.class);
        OffsetDateTime submittedAt = OffsetDateTime.parse("2026-10-16T12:00:00Z");
        when(order.getOrderId()).thenReturn(5);
        when(order.getInstrument()).thenReturn(instrument);
        when(instrument.getInstrumentId()).thenReturn(7);
        when(instrument.getSymbol()).thenReturn("AAPL");
        when(instrument.getQuoteCurrency()).thenReturn(Currency.USD);
        when(order.getAccount()).thenReturn(mock(com.neueda.leap.entities.Account.class));
        when(order.getSide()).thenReturn(OrderSide.SELL);
        when(order.getRequestedQuantity()).thenReturn(BigDecimal.ONE);
        when(order.getSubmittedQuotePrice()).thenReturn(new BigDecimal("100"));
        when(order.getSubmittedQuoteAt()).thenReturn(submittedAt);
        when(order.getSubmittedAt()).thenReturn(submittedAt);
        when(event.getStatus()).thenReturn(OrderStatus.FILLED);
        when(event.getDecisionQuotePrice()).thenReturn(new BigDecimal("102.25"));

        OrderResponse filled = OrderResponse.from(order, event);
        assertThat(filled.executionPrice()).isEqualByComparingTo("102.25");
        assertThat(filled.indicativePrice()).isEqualByComparingTo("100");
        assertThat(filled.currency()).isEqualTo(Currency.USD);

        when(event.getStatus()).thenReturn(OrderStatus.ACCEPTED);
        assertThat(OrderResponse.from(order, event).executionPrice()).isNull();
    }
}
