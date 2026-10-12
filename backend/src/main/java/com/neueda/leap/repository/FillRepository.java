package com.neueda.leap.repository;

import com.neueda.leap.entities.Fill;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FillRepository extends Repository<Fill, Integer> {
    Fill save(Fill fill);

    @Query(
        value = """
            SELECT f.*
            FROM fills f
            JOIN orders o on o.order_id = f.order_id
            JOIN accounts a on a.account_id = o.account_id
            WHERE f.order_id = :orderId
            AND a.client_id = :clientId
            """, nativeQuery = true
    )
    Optional<Fill> findByOrderIdAndClientId(
        @Param("orderId") Integer orderId,
        @Param("clientId") Integer clientId
    );
}