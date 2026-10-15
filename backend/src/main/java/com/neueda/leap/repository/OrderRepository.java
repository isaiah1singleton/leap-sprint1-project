package com.neueda.leap.repository;

import com.neueda.leap.entities.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM orders o
                JOIN order_events e ON e.order_id = o.order_id
                WHERE o.account_id = :accountId
                  AND e.event_id = (
                      SELECT MAX(latest.event_id)
                      FROM order_events latest
                      WHERE latest.order_id = o.order_id
                  )
                  AND e.status = 'ACCEPTED'
            )
            """, nativeQuery = true)
    boolean existsPendingAcceptedOrder(
            @Param("accountId") Integer accountId
    );

    @Query(value = """
            SELECT o.order_id
            FROM orders o
            JOIN order_events e ON e.order_id = o.order_id
            WHERE e.event_id = (
                SELECT MAX(latest.event_id)
                FROM order_events latest
                WHERE latest.order_id = o.order_id
            )
              AND e.status = 'ACCEPTED'
            ORDER BY e.occurred_at ASC, o.order_id ASC
            """, nativeQuery = true)
    List<Integer> findPendingAcceptedOrderIds(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT o
            FROM Order o
            WHERE o.orderId = :orderId
            """)
    Optional<Order> findByIdForUpdate(
            @Param("orderId") Integer orderId
    );

    @EntityGraph(attributePaths = {"account", "instrument"})
    List<Order>
    findByAccount_AccountIdAndAccount_Client_ClientIdOrderBySubmittedAtDesc(
            Integer accountId,
            Integer clientId
    );

    @EntityGraph(attributePaths = {"account", "instrument"})
    Optional<Order> findByOrderIdAndAccount_Client_ClientId(
            Integer orderId,
            Integer clientId
    );
    @Query("""
        SELECT o.instrument.symbol
        FROM Order o
        WHERE o.orderId = :orderId
        """)
    Optional<String> findInstrumentSymbolByOrderId(
            @Param("orderId") Integer orderId
    );
}
