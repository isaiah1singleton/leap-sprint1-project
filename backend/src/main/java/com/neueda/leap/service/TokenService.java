package com.neueda.leap.service;

import com.neueda.leap.entities.AuthSession;
import com.neueda.leap.entities.Client;
import com.neueda.leap.enums.ClientStatus;
import com.neueda.leap.repository.AuthSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {

    private static final int TOKEN_BYTES = 32;
    private final AuthSessionRepository sessionRepository;
    private final Clock clock;
    private final Duration absoluteTimeout;
    private final Duration idleTimeout;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(
            AuthSessionRepository sessionRepository,
            Clock clock,
            @Value("${auth.session.absolute-timeout:PT24H}") Duration absoluteTimeout,
            @Value("${auth.session.idle-timeout:PT30M}") Duration idleTimeout) {
        if (absoluteTimeout.isNegative() || absoluteTimeout.isZero()
                || idleTimeout.isNegative() || idleTimeout.isZero()) {
            throw new IllegalArgumentException("Session timeouts must be positive.");
        }
        this.sessionRepository = sessionRepository;
        this.clock = clock;
        this.absoluteTimeout = absoluteTimeout;
        this.idleTimeout = idleTimeout;
    }

    @Transactional
    public TokenGrant issueToken(Client client) {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        OffsetDateTime createdAt = OffsetDateTime.now(clock);
        OffsetDateTime expiresAt = createdAt.plus(absoluteTimeout);

        sessionRepository.save(new AuthSession(hashToken(token), client, createdAt, expiresAt));
        return new TokenGrant(token, expiresAt);
    }

    @Transactional
    public Optional<String> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        return sessionRepository.findActiveSession(hashToken(token), now, now.minus(idleTimeout))
                .filter(session -> session.getClient().getClientStatus() == ClientStatus.ACTIVE)
                .map(session -> {
                    session.recordActivity(now);
                    return session.getClient().getEmail();
                });
    }

    @Transactional
    public boolean revokeToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        return sessionRepository.findActiveSession(hashToken(token), now, now.minus(idleTimeout))
                .filter(session -> session.getClient().getClientStatus() == ClientStatus.ACTIVE)
                .map(session -> {
                    session.revoke(now);
                    return true;
                })
                .orElse(false);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }

    public record TokenGrant(String accessToken, OffsetDateTime expiresAt) {
    }
}
