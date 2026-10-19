package com.neueda.leap.repository;

import com.neueda.leap.entities.CashBalance;
import com.neueda.leap.entities.CashBalanceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CashBalanceRepository extends JpaRepository<CashBalance, CashBalanceId> {

    @Query("""
            select balance
            from CashBalance balance
            where balance.account.accountId = :accountId
            and balance.account.client.clientId = :clientId
            order by balance.id.currency
            """)
    List<CashBalance> findOwnedBalances(@Param("accountId") Integer accountId, @Param("clientId") Integer clientId);
    
    @Query("""
            select balance
            from CashBalance balance
            where balance.account.accountId = :accountId
                and balance.id.currency = :currency
                and balance.account.client.clientId = :clientId
            """)
    List<CashBalance> findOwnedBalance(@Param("accountId") Integer accountId, @Param("currency") String currency, @Param("clientId") Integer clientId);

}
