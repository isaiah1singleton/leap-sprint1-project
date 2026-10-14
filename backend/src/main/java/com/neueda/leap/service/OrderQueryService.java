package com.neueda.leap.service;

import com.neueda.leap.entities.Order;
import com.neueda.leap.entities.OrderEvent;
import com.neueda.leap.models.OrderResponse;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.OrderEventRepository;
import com.neueda.leap.repository.OrderRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderQueryService {
    private final AccountRepository accounts;
    private final OrderRepository orders;
    private final OrderEventRepository events;

    public OrderQueryService(AccountRepository accounts, OrderRepository orders, OrderEventRepository events) {
        this.accounts = accounts;
        this.orders = orders;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(Integer accountId, Integer clientId) {
        accounts.findByAccountIdAndClient_ClientId(accountId, clientId)
                .orElseThrow(() -> new NoSuchElementException("Account not found."));
        return orders.findByAccount_AccountIdAndAccount_Client_ClientIdOrderBySubmittedAtDesc(accountId, clientId)
                .stream().map(order -> OrderResponse.from(order, latest(order.getOrderId()))).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Integer orderId, Integer clientId) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("Order ID must be positive.");
        }
        Order order = orders.findByOrderIdAndAccount_Client_ClientId(orderId, clientId)
                .orElseThrow(() -> new NoSuchElementException("Order not found."));
        return OrderResponse.from(order, latest(orderId));
    }

    private OrderEvent latest(Integer orderId) {
        return events.findFirstByOrder_OrderIdOrderByOrderEventIdDesc(orderId)
                .orElseThrow(() -> new IllegalStateException("Order has no status event."));
    }
}
