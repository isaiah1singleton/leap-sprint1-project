package com.neueda.leap.repository;

import com.neueda.leap.entities.Holding;
import com.neueda.leap.entities.HoldingId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HoldingRepository
        extends JpaRepository<Holding, HoldingId> {

    @Query("""
            select holding
            from Holding holding
            where holding.account.accountId = :accountId
              and holding.account.client.clientId = :clientId
            order by holding.instrument.instrumentId
            """)
    List<Holding> findOwnedHoldings(
            @Param("accountId") Integer accountId,
            @Param("clientId") Integer clientId
    );

    @Query("""
            select holding
            from Holding holding
            where holding.account.accountId = :accountId
              and holding.instrument.instrumentId = :instrumentId
              and holding.account.client.clientId = :clientId
            """)
    Optional<Holding> findOwnedHolding(
            @Param("accountId") Integer accountId,
            @Param("instrumentId") Integer instrumentId,
            @Param("clientId") Integer clientId
    );
}
