package com.neueda.leap.service;

import com.neueda.leap.entities.*;
import com.neueda.leap.enums.ClientStatus;
import com.neueda.leap.repository.AuthSessionRepository;
import java.time.Duration;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Temporary regression coverage for the current opaque sessions, not a JWT contract. */
@Tag("unit") @Tag("legacy-auth")
class LegacyTokenServiceTest {
    private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    private final TokenService service = new TokenService(sessions, CLOCK, Duration.ofHours(24), Duration.ofMinutes(30));
    @Test void issueStoresOnlyHashAndUsesConfiguredExpiry() throws Exception {
        var grant = service.issueToken(client()); var second = service.issueToken(client());
        assertThat(grant.accessToken()).isNotEqualTo(second.accessToken()); assertThat(grant.expiresAt()).isEqualTo(AT.plusDays(1));
        assertThat(Base64.getUrlDecoder().decode(grant.accessToken())).hasSize(32);
        ArgumentCaptor<AuthSession> saved = ArgumentCaptor.forClass(AuthSession.class); verify(sessions, times(2)).save(saved.capture());
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(grant.accessToken().getBytes(StandardCharsets.UTF_8)));
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(saved.getAllValues().get(0), "sessionTokenHash")).isEqualTo(hash);
        assertThat(saved.getAllValues().get(0).getCreatedAt()).isEqualTo(AT);
    }
    @Test void blankTokensNeverQueryDatabaseAndUnknownTokensAreRejected() {
        for (String t : new String[]{null, "", " "}) { assertThat(service.authenticate(t)).isEmpty(); assertThat(service.revokeToken(t)).isFalse(); }
        verifyNoInteractions(sessions); assertThat(service.authenticate("unknown")).isEmpty(); assertThat(service.revokeToken("unknown")).isFalse();
    }
    @Test void activeSessionUpdatesActivityAndRevokesButInactiveClientCannotAuthenticate() {
        var session = new AuthSession("hash", client(), AT.minusMinutes(1), AT.plusDays(1));
        when(sessions.findActiveSession(anyString(), eq(AT), eq(AT.minusMinutes(30)))).thenReturn(Optional.of(session));
        assertThat(service.authenticate("token")).contains("alice@example.com"); assertThat(session.getLastActivityAt()).isEqualTo(AT);
        assertThat(service.revokeToken("token")).isTrue(); assertThat(session.getRevokedAt()).isEqualTo(AT);
        id(session.getClient(), "clientStatus", ClientStatus.INACTIVE);
        assertThat(service.authenticate("token")).isEmpty(); assertThat(service.revokeToken("token")).isFalse();
    }
    @Test void nonpositiveTimeoutsRejected() {
        for (Duration invalid : List.of(Duration.ZERO, Duration.ofSeconds(-1))) {
            assertThatIllegalArgumentException().isThrownBy(() -> new TokenService(sessions, CLOCK, invalid, Duration.ofMinutes(1)));
            assertThatIllegalArgumentException().isThrownBy(() -> new TokenService(sessions, CLOCK, Duration.ofMinutes(1), invalid));
        }
    }
}
