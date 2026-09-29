package com.neueda.leap.service;

import com.neueda.leap.entities.AuthSession;
import com.neueda.leap.entities.Client;
import com.neueda.leap.enums.ClientStatus;
import com.neueda.leap.repository.AuthSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {

    private static final int TOKEN_BYTES = 32;
    private static final long TOKEN_LIFETIME_HOURS = 24;

    private final AuthSessionRepository sessionRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(AuthSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public TokenGrant issueToken(Client client) {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(TOKEN_LIFETIME_HOURS);

        sessionRepository.save(new AuthSession(hashToken(token), client, expiresAt));
        return new TokenGrant(token, expiresAt);
    }

    @Transactional(readOnly = true)
    public Optional<String> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        return sessionRepository.findBySessionTokenHashAndRevokedFalse(hashToken(token))
                .filter(session -> session.getExpiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC)))
                .map(AuthSession::getClient)
                .filter(client -> client.getClientStatus() == ClientStatus.ACTIVE)
                .map(Client::getEmail);
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
