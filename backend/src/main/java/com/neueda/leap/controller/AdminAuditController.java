package com.neueda.leap.controller;

import com.neueda.leap.ApiRoutes;
import com.neueda.leap.models.AuditRecordResponse;
import com.neueda.leap.models.TradeLifecycleResponse;
import com.neueda.leap.service.AuditRecordService;
import com.neueda.leap.service.ClientService;
import com.neueda.leap.service.SuspiciousTradeService;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(ApiRoutes.ADMIN_AUDIT)
public class AdminAuditController {
    private final AuditRecordService auditRecords;
    private final ClientService clients;
    private final SuspiciousTradeService suspiciousTrades;

    public AdminAuditController(AuditRecordService auditRecords, ClientService clients,
                                SuspiciousTradeService suspiciousTrades) {
        this.auditRecords = auditRecords;
        this.clients = clients;
        this.suspiciousTrades = suspiciousTrades;
    }

    @GetMapping
    public List<AuditRecordResponse> latest(
            Principal principal,
            @RequestParam(defaultValue = "100") int limit) {
        requireAdmin(principal);
        return auditRecords.latest(limit);
    }

    @GetMapping(ApiRoutes.ADMIN_AUDIT_LIFECYCLE)
    public TradeLifecycleResponse lifecycle(Principal principal, @PathVariable Integer orderId) {
        requireAdmin(principal);
        return auditRecords.lifecycle(orderId);
    }

    @PostMapping(ApiRoutes.ADMIN_AUDIT_SUSPICIOUS)
    public void flagSuspicious(Principal principal, @PathVariable Integer orderId) {
        requireAdmin(principal);
        suspiciousTrades.flag(orderId, principal.getName());
    }

    @DeleteMapping(ApiRoutes.ADMIN_AUDIT_SUSPICIOUS)
    public void removeSuspiciousFlag(Principal principal, @PathVariable Integer orderId) {
        requireAdmin(principal);
        suspiciousTrades.removeFlag(orderId);
    }

    private void requireAdmin(Principal principal) {
        if (principal == null || !clients.isActiveAdmin(principal.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required.");
        }
    }
}