package com.neueda.leap.repository;

import com.neueda.leap.entities.AuditRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {
    boolean existsByEventId(UUID eventId);
    List<AuditRecord> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
