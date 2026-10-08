package com.neueda.leap.repository;

import com.neueda.leap.entities.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    // List the order made by an account
    List<Order> findByAccount_AccountId(Integer accountId);

}
