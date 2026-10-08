package com.neueda.leap.repository;

import com.neueda.leap.entities.CashMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;


public interface CashMovementRepository extends JpaRepository<CashMovement, Integer>{
    @Query("""
            select movement
            from CashMovement movement
            where movement.account.accountId = :accountId
                and movement.account.client.clientId = :clientId
            order by movement.occurredAt asc,
                movement.cashMovementId asc
            """)
    List<CashMovement> findOwnedHistory(
        @Param("accountId") Integer accountId,
        @Param("clientId") Integer clientId
    );

    @Query("""
            select movement
            from CashMovement movement
            where movement.cashMovementId = :movementId
                and movement.account.client.clientId = :clientId
            """)
    Optional<CashMovement> findOwnedMovement(
        @Param("movementId") Integer movementId,
        @Param("clientId") Integer clientId
    );
    
}
