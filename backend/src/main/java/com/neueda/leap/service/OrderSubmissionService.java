package com.neueda.leap.service;

import com.neueda.leap.entities.Account;
import com.neueda.leap.entities.Instrument;
import com.neueda.leap.entities.Order;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.models.OrderResponse;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.InstrumentRepository;
import com.neueda.leap.repository.OrderRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;


@Service
public class OrderSubmissionService {
    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final InstrumentRepository instrumentRepository;

    public OrderSubmissionService(
            OrderRepository orderRepository,
            AccountRepository accountRepository,
            InstrumentRepository instrumentRepository
    ){
        this.orderRepository = orderRepository;
        this.accountRepository = accountRepository;
        this.instrumentRepository = instrumentRepository;
    }

    @Transactional
    public OrderResponse submitOrder
            (Integer accountId, Integer instrumentId, OrderSide side, BigDecimal requestedQuantity,
             BigDecimal submittedQuotePrice){
        // to-do: need to add the submittedAt and submittedQuoteAt argument
        OffsetDateTime submittedAt = OffsetDateTime.now();

        // to-do: check that account is not flagged

        // check that quantity is positive
        if (requestedQuantity.compareTo(BigDecimal.ZERO) <= 0){
            throw new IllegalArgumentException("Quantity must be greater than 0.");
        }

        // this is where we recheck fauxnance to lock in price

        // to-do: check cash balance against new price

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NoSuchElementException("Account not found."));

        Instrument instrument = instrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new NoSuchElementException("Instrument not found."));

        Order order = new Order(account, instrument, side, requestedQuantity, submittedQuotePrice, submittedAt);

        Order savedOrder = orderRepository.save(order);
        return OrderResponse.from(savedOrder);
    }
}
