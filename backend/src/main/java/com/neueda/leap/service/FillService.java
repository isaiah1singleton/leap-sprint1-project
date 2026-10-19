package com.neueda.leap.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;


import com.neueda.leap.entities.Fill;
import com.neueda.leap.entities.Order;
import com.neueda.leap.models.FillResponse;
import com.neueda.leap.repository.FillRepository;

@Service 
public class FillService {
    private final FillRepository fillRepository;
    
    public FillService(FillRepository fillRepository) {
        this.fillRepository = fillRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Fill recordFill(
        Order order,
        BigDecimal executionPrice,
        OffsetDateTime executionTime
    ) {
        if (order == null) {
            throw new IllegalArgumentException("Order is required");
        }
        if (order.getOrderId() == null || order.getOrderId() <= 0) {
            throw new IllegalArgumentException("Order must already be persisted.");
        }
        Fill fill = new Fill(order, executionPrice, executionTime);
        
        return fillRepository.save(fill);
    }

    @Transactional(readOnly = true)
    public FillResponse getFillFromOrder(Integer orderId, Integer authenticatedClientId) {
        requirePositiveId(orderId, "Order");
        requirePositiveId(authenticatedClientId, "client");

        Fill fill = fillRepository.findByOrderIdAndClientId(orderId, authenticatedClientId).orElseThrow(() -> new NoSuchElementException("Fill not found."));
        return FillResponse.from(fill);
    }

    private static void requirePositiveId(
            Integer id,
            String field
    ) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    field + " ID must be positive."
            );
        }
    } 
}
