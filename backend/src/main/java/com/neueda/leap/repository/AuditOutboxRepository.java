package com.neueda.leap.repository;

import com.neueda.leap.entities.AuditOutbox;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditOutboxRepository extends JpaRepository<AuditOutbox, Long> {
    List<AuditOutbox> findByPublishedAtIsNullOrderByOutboxIdAsc(Pageable pageable);
}
