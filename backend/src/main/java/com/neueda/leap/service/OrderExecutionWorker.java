package com.neueda.leap.service;

import com.neueda.leap.models.MarketQuoteResultDto;
import com.neueda.leap.models.MarketQuotesDto;
import com.neueda.leap.repository.OrderRepository;
import com.neueda.leap.service.OrderExecutionService.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(
        name = "trading.execution.enabled",
        havingValue = "true"
)
public class OrderExecutionWorker {

    private static final Logger log =
            LoggerFactory.getLogger(OrderExecutionWorker.class);

    private static final int MAX_BATCH_SIZE = 25;

    private final OrderRepository orders;
    private final MarketDataService marketData;
    private final OrderExecutionService execution;
    private final int batchSize;

    public OrderExecutionWorker(
            OrderRepository orders,
            MarketDataService marketData,
            OrderExecutionService execution,
            @Value("${trading.execution.batch-size:25}") int batchSize
    ) {
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "Execution batch size must be between 1 and 25."
            );
        }

        this.orders = orders;
        this.marketData = marketData;
        this.execution = execution;
        this.batchSize = batchSize;
    }

    @Scheduled(
            initialDelayString =
                    "${trading.execution.initial-delay-ms:5000}",
            fixedDelayString =
                    "${trading.execution.poll-delay-ms:2000}"
    )
    public void executePendingOrders() {
        List<Integer> orderIds;

        try {
            orderIds = orders.findPendingAcceptedOrderIds(
                    PageRequest.of(0, batchSize)
            );
        } catch (RuntimeException failure) {
            log.error("Could not load pending orders.", failure);
            return;
        }

        if (orderIds.isEmpty()) {
            return;
        }

        Map<Integer, String> candidates = prepareCandidates(orderIds);

        if (candidates.isEmpty()) {
            return;
        }

        Map<String, MarketQuoteResultDto> quotes =
                fetchQuotes(candidates);

        for (Map.Entry<Integer, String> candidate
                : candidates.entrySet()) {
            Integer orderId = candidate.getKey();
            MarketQuoteResultDto quote =
                    quotes.get(candidate.getValue());

            try {
                ExecutionResult result =
                        execution.execute(orderId, quote);

                log.debug(
                        "Execution attempt for order {} returned {}.",
                        orderId,
                        result
                );
            } catch (RuntimeException failure) {
                // The execution transaction has rolled back.
                // Continue processing the other orders.
                log.error(
                        "Execution attempt failed for order {}.",
                        orderId,
                        failure
                );
            }
        }
    }

    private Map<Integer, String> prepareCandidates(
            List<Integer> orderIds
    ) {
        Map<Integer, String> candidates = new LinkedHashMap<>();

        for (Integer orderId : orderIds) {
            try {
                // Expire overdue orders before contacting the provider.
                // With no quote, a still-pending order returns RETRY.
                ExecutionResult result =
                        execution.execute(orderId, null);

                if (result != ExecutionResult.RETRY) {
                    continue;
                }

                String symbol = orders
                        .findInstrumentSymbolByOrderId(orderId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Pending order has no instrument symbol."
                                )
                        );

                candidates.put(orderId, symbol);
            } catch (RuntimeException failure) {
                log.error(
                        "Could not prepare order {} for execution.",
                        orderId,
                        failure
                );
            }
        }

        return candidates;
    }

    private Map<String, MarketQuoteResultDto> fetchQuotes(
            Map<Integer, String> candidates
    ) {
        Map<String, MarketQuoteResultDto> quotes = new HashMap<>();

        String symbols = String.join(
                ",",
                new LinkedHashSet<>(candidates.values())
        );

        try {
            MarketQuotesDto response = marketData.quotes(symbols);

            if (response == null || response.quotes() == null) {
                log.warn("Market data returned no execution quotes.");
                return quotes;
            }

            for (MarketQuoteResultDto quote : response.quotes()) {
                if (quote != null && quote.symbol() != null) {
                    quotes.put(quote.symbol(), quote);
                }
            }
        } catch (RuntimeException failure) {
            // Avoid logging provider response bodies or credentials.
            log.warn(
                    "Execution quotes could not be retrieved ({}). "
                            + "Pending orders will be checked for expiry.",
                    failure.getClass().getSimpleName()
            );
        }

        return quotes;
    }
}
