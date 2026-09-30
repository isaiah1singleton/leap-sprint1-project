package com.neueda.leap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neueda.leap.entities.AuthSession;
import com.neueda.leap.entities.Client;
import com.neueda.leap.repository.AuthSessionRepository;
import com.neueda.leap.repository.ClientRepository;
import com.neueda.leap.service.TokenService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:session-lifecycle;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:../database/transaction_schema.sql"
})
@AutoConfigureMockMvc
class SessionLifecycleTest {

    private static final Instant START = Instant.parse("2026-09-29T09:00:00Z");

    @Autowired private TokenService tokenService;
    @Autowired private ClientRepository clientRepository;
    @Autowired private AuthSessionRepository sessionRepository;
    @Autowired private MockMvc mockMvc;
    @Autowired private MutableClock clock;

    @BeforeEach
    void resetDatabaseAndTime() {
        sessionRepository.deleteAll();
        clientRepository.deleteAll();
        clock.set(START);
    }

    @Test
    void issueCreatesA24HourSessionWithOnlyTheTokenHashStored() {
        TokenService.TokenGrant grant = issueToken();
        AuthSession session = onlySession();

        assertThat(grant.expiresAt()).isEqualTo(at(START.plusSeconds(24 * 60 * 60)));
        assertThat(session.getCreatedAt()).isEqualTo(at(START));
        assertThat(session.getLastActivityAt()).isEqualTo(at(START));
        assertThat(session.getExpiresAt()).isEqualTo(grant.expiresAt());
        assertThat(session.getRevokedAt()).isNull();
        assertThat(sessionRepository.findBySessionTokenHash(hashToken(grant.accessToken()))).isPresent();
        assertThat(sessionRepository.findBySessionTokenHash(grant.accessToken())).isEmpty();
    }

    @Test
    void acceptedRequestRefreshesIdleActivityButNotTheAbsoluteDeadline() {
        TokenService.TokenGrant grant = issueToken();
        clock.set(START.plusSeconds(29 * 60));

        assertThat(tokenService.authenticate(grant.accessToken())).contains("alice@example.com");
        assertThat(onlySession().getLastActivityAt()).isEqualTo(at(START.plusSeconds(29 * 60)));
        assertThat(onlySession().getExpiresAt()).isEqualTo(grant.expiresAt());

        clock.set(START.plusSeconds(58 * 60));
        assertThat(tokenService.authenticate(grant.accessToken())).contains("alice@example.com");
        assertThat(onlySession().getLastActivityAt()).isEqualTo(at(START.plusSeconds(58 * 60)));
    }

    @Test
    void idleDeadlineRejectsTokenAndKeepsHistoricalRowUnchanged() {
        TokenService.TokenGrant grant = issueToken();
        clock.set(START.plusSeconds(30 * 60));

        assertThat(tokenService.authenticate(grant.accessToken())).isEmpty();
        assertThat(sessionRepository.count()).isEqualTo(1);
        assertThat(onlySession().getLastActivityAt()).isEqualTo(at(START));
        assertThat(onlySession().getRevokedAt()).isNull();
    }

    @Test
    void idleDeadlineReturnsUnauthorizedAtTheApiBoundary() throws Exception {
        TokenService.TokenGrant grant = issueToken();
        clock.set(START.plusSeconds(30 * 60));

        mockMvc.perform(get("/api/accounts")
                        .header("Authorization", "Bearer " + grant.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void absoluteDeadlineRejectsTokenEvenWhenRecentActivityExists() {
        TokenService.TokenGrant grant = issueToken();
        OffsetDateTime recentActivity = at(START.plusSeconds(24 * 60 * 60 - 60));
        AuthSession session = onlySession();
        session.recordActivity(recentActivity);
        sessionRepository.saveAndFlush(session);
        clock.set(START.plusSeconds(24 * 60 * 60));

        assertThat(tokenService.authenticate(grant.accessToken())).isEmpty();
        assertThat(sessionRepository.count()).isEqualTo(1);
        assertThat(onlySession().getLastActivityAt()).isEqualTo(recentActivity);
    }

    @Test
    void signOutRevokesTheServerSessionAndFurtherUseIsUnauthorized() throws Exception {
        TokenService.TokenGrant grant = issueToken();
        clock.set(START.plusSeconds(60));

        mockMvc.perform(post("/api/auth/sign-out")
                        .header("Authorization", "Bearer " + grant.accessToken()))
                .andExpect(status().isNoContent());

        assertThat(onlySession().getRevokedAt()).isEqualTo(at(START.plusSeconds(60)));
        assertThat(tokenService.authenticate(grant.accessToken())).isEmpty();
        mockMvc.perform(get("/api/accounts")
                        .header("Authorization", "Bearer " + grant.accessToken()))
                .andExpect(status().isUnauthorized());
        assertThat(sessionRepository.count()).isEqualTo(1);
    }

    @Test
    void signOutRequiresAnAuthenticatedToken() throws Exception {
        mockMvc.perform(post("/api/auth/sign-out"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationAndSignInIgnoreAnInvalidBearerHeader() throws Exception {
        String credentials = "{\"email\":\"alice@example.com\",\"password\":\"password123\"}";

        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer invalid")
                        .contentType(APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/sign-in")
                        .header("Authorization", "Bearer invalid")
                        .contentType(APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isOk());
    }

    @Test
    void accountRoutesRequireAValidBearerToken() throws Exception {
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/accounts")
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());

        TokenService.TokenGrant grant = issueToken();
        mockMvc.perform(get("/api/accounts")
                        .header("Authorization", "Bearer " + grant.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void helloRemainsPublicWithAnInvalidBearerHeader() throws Exception {
        mockMvc.perform(get("/hello")
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isOk());
    }

    private TokenService.TokenGrant issueToken() {
        Client client = clientRepository.save(new Client("alice@example.com", "unused-test-hash"));
        return tokenService.issueToken(client);
    }

    private AuthSession onlySession() {
        return sessionRepository.findAll().get(0);
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new AssertionError(ex);
        }
    }

    @TestConfiguration
    static class TestClockConfiguration {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> time = new AtomicReference<>(START);

        void set(Instant instant) {
            time.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return time.get();
        }
    }
}
