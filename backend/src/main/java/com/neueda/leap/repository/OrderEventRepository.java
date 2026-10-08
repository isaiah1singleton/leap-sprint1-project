package com.neueda.leap.repository;

import com.neueda.leap.entities.OrderEvent;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderEventRepository extends Repository<OrderEvent, Integer> {

    OrderEvent save(OrderEvent event);

    Optional<OrderEvent>
    findFirstByOrder_OrderIdOrderByOrderEventIdDesc(Integer orderId);

    List<OrderEvent>
    findByOrder_OrderIdOrderByOrderEventIdAsc(Integer orderId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM orders o
                JOIN accounts a ON a.account_id = o.account_id
                WHERE o.order_id = :orderId
                  AND a.client_id = :clientId
            )
            """, nativeQuery = true)
    boolean existsOwnedOrder(
            @Param("orderId") Integer orderId,
            @Param("clientId") Integer clientId
    );
}
