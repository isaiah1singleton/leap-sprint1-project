package com.neueda.leap.service;

import com.neueda.leap.models.*;
import com.neueda.leap.repository.OrderRepository;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static com.neueda.leap.support.TradingFixtures.*;
import static com.neueda.leap.service.OrderExecutionService.ExecutionResult.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class OrderExecutionWorkerTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final MarketDataService market = mock(MarketDataService.class);
    private final OrderExecutionService execution = mock(OrderExecutionService.class);
    private final OrderExecutionWorker worker = new OrderExecutionWorker(orders, market, execution, 25);
    @Test void invalidBatchSizesRejected() {
        for (int n : new int[]{-1, 0, 26}) assertThatIllegalArgumentException().isThrownBy(() -> new OrderExecutionWorker(orders, market, execution, n));
    }
    @Test void emptyOrFailedLookupDoesNotContactProvider() {
        worker.executePendingOrders(); verifyNoInteractions(market, execution);
        when(orders.findPendingAcceptedOrderIds(any())).thenThrow(new IllegalStateException("db"));
        assertThatCode(worker::executePendingOrders).doesNotThrowAnyException(); verifyNoInteractions(market, execution);
    }
    @Test void expiresFirstDeduplicatesSymbolsAndContinuesAfterFailure() {
        when(orders.findPendingAcceptedOrderIds(any())).thenReturn(List.of(1, 2, 3, 4));
        when(execution.execute(1, null)).thenReturn(EXPIRED);
        for (int id : new int[]{2, 3, 4}) { when(execution.execute(id, null)).thenReturn(RETRY); when(orders.findInstrumentSymbolByOrderId(id)).thenReturn(Optional.of("AAPL")); }
        var q = quote(); when(market.quotes("AAPL")).thenReturn(new MarketQuotesDto(List.of(q), ""));
        when(execution.execute(2, q)).thenThrow(new IllegalStateException("settlement"));
        worker.executePendingOrders();
        verify(market).quotes("AAPL"); verify(execution).execute(3, q); verify(execution).execute(4, q);
        verify(orders, never()).findInstrumentSymbolByOrderId(1);
        var sequence = inOrder(execution, market); sequence.verify(execution).execute(1, null); sequence.verify(execution).execute(2, null);
        sequence.verify(execution).execute(3, null); sequence.verify(execution).execute(4, null); sequence.verify(market).quotes("AAPL");
    }
    @Test void preparationFailuresAndMissingSymbolsAreIsolated() {
        when(orders.findPendingAcceptedOrderIds(any())).thenReturn(List.of(1, 2, 3));
        when(execution.execute(1, null)).thenThrow(new IllegalStateException("db"));
        when(execution.execute(2, null)).thenReturn(RETRY); when(execution.execute(3, null)).thenReturn(SKIPPED);
        worker.executePendingOrders(); verifyNoInteractions(market);
    }
    @Test void unavailableProviderStillAllowsFurtherExecutionExpiryChecks() {
        when(orders.findPendingAcceptedOrderIds(any())).thenReturn(List.of(3));
        when(execution.execute(3, null)).thenReturn(RETRY); when(orders.findInstrumentSymbolByOrderId(3)).thenReturn(Optional.of("AAPL"));
        when(market.quotes("AAPL")).thenThrow(new IllegalStateException("provider"));
        worker.executePendingOrders(); verify(execution, times(2)).execute(3, null);
    }
    @Test void nullAndIncompleteBatchesDoNotInventQuotes() {
        when(orders.findPendingAcceptedOrderIds(any())).thenReturn(List.of(3));
        when(execution.execute(3, null)).thenReturn(RETRY); when(orders.findInstrumentSymbolByOrderId(3)).thenReturn(Optional.of("AAPL"));
        worker.executePendingOrders();
        when(market.quotes("AAPL")).thenReturn(new MarketQuotesDto(null, "")); worker.executePendingOrders();
        when(market.quotes("AAPL")).thenReturn(new MarketQuotesDto(Arrays.asList(null, new MarketQuoteResultDto(null, null, null, false, null)), "")); worker.executePendingOrders();
        verify(execution, times(6)).execute(3, null);
    }
}
