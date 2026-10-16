package com.neueda.leap.controller;

import com.neueda.leap.ApiRoutes;
import com.neueda.leap.models.AuditRecordResponse;
import com.neueda.leap.service.AuditRecordService;
import com.neueda.leap.service.ClientService;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(ApiRoutes.ADMIN_AUDIT)
public class AdminAuditController {
    private final AuditRecordService auditRecords;
    private final ClientService clients;

    public AdminAuditController(AuditRecordService auditRecords, ClientService clients) {
        this.auditRecords = auditRecords;
        this.clients = clients;
    }

    @GetMapping
    public List<AuditRecordResponse> latest(
            Principal principal,
            @RequestParam(defaultValue = "100") int limit) {
        requireAdmin(principal);
        return auditRecords.latest(limit);
    }

    private void requireAdmin(Principal principal) {
        if (principal == null || !clients.isActiveAdmin(principal.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required.");
        }
    }
}