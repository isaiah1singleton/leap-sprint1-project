package com.neueda.leap.repository;

import com.neueda.leap.entities.AuthSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Integer> {
    Optional<AuthSession> findBySessionTokenHashAndRevokedFalse(String sessionTokenHash);
}
