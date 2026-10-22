package com.neueda.leap.repository;

import com.neueda.leap.entities.SuspiciousTradeFlag;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuspiciousTradeFlagRepository extends JpaRepository<SuspiciousTradeFlag, Long> {
    Optional<SuspiciousTradeFlag> findByOrderId(Integer orderId);
    List<SuspiciousTradeFlag> findAllByOrderIdIn(Collection<Integer> orderIds);
    boolean existsByAccountId(Integer accountId);
}
