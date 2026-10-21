package com.neueda.leap.service;

import com.neueda.leap.entities.Account;
import com.neueda.leap.entities.Order;
import com.neueda.leap.entities.SuspiciousTradeFlag;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.OrderRepository;
import com.neueda.leap.repository.SuspiciousTradeFlagRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SuspiciousTradeService {
    private final OrderRepository orders;
    private final AccountRepository accounts;
    private final SuspiciousTradeFlagRepository flags;
    private final Clock clock;

    public SuspiciousTradeService(OrderRepository orders, AccountRepository accounts,
                                  SuspiciousTradeFlagRepository flags, Clock clock) {
        this.orders = orders;
        this.accounts = accounts;
        this.flags = flags;
        this.clock = clock;
    }

    @Transactional
    public void flag(Integer orderId, String flaggedBy) {
        if (flags.findByOrderId(orderId).isPresent()) {
            return;
        }
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trade not found."));
        Account account = accounts.findByAccountId(order.getAccount().getAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
        AccountStatus previousStatus = account.getAccountStatus();
        account.changeStatus(AccountStatus.INACTIVE);
        flags.save(new SuspiciousTradeFlag(orderId, account.getAccountId(), flaggedBy,
                OffsetDateTime.now(clock), previousStatus));
    }

    @Transactional
    public void removeFlag(Integer orderId) {
        SuspiciousTradeFlag flag = flags.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Suspicious trade flag not found."));
        Account account = accounts.findByAccountId(flag.getAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
        flags.delete(flag);
        if (!flags.existsByAccountId(account.getAccountId())
                && account.getAccountStatus() == AccountStatus.INACTIVE
                && flag.getPreviousAccountStatus() == AccountStatus.ACTIVE) {
            account.changeStatus(AccountStatus.ACTIVE);
        }
    }
}
