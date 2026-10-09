package com.neueda.leap.repository;

import com.neueda.leap.entities.Order;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Integer> {
    @EntityGraph(attributePaths = {"account", "instrument"})
    List<Order> findByAccount_AccountIdAndAccount_Client_ClientIdOrderBySubmittedAtDesc(
            Integer accountId, Integer clientId);

    @EntityGraph(attributePaths = {"account", "instrument"})
    Optional<Order> findByOrderIdAndAccount_Client_ClientId(Integer orderId, Integer clientId);
}
