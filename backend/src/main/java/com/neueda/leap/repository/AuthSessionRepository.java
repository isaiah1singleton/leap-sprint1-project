package com.neueda.leap.repository;

import com.neueda.leap.entities.AuthSession;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Integer> {
    Optional<AuthSession> findBySessionTokenHash(String sessionTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session from AuthSession session
            where session.sessionTokenHash = :tokenHash
              and session.revoked = false
              and session.expiresAt > :now
              and session.lastActivityAt > :idleCutoff
            """)
    Optional<AuthSession> findActiveSession(
            @Param("tokenHash") String tokenHash,
            @Param("now") OffsetDateTime now,
            @Param("idleCutoff") OffsetDateTime idleCutoff);
}
