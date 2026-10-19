package com.neueda.leap.service;

import com.neueda.leap.entities.AuditOutbox;
import com.neueda.leap.models.TradeAuditEvent;
import com.neueda.leap.repository.AuditOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditOutboxService {
    private final AuditOutboxRepository repository;

    public AuditOutboxService(AuditOutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(TradeAuditEvent event) {
        repository.save(new AuditOutbox(event));
    }
}
