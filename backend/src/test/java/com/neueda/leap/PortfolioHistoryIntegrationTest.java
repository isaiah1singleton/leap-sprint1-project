package com.neueda.leap;

import com.neueda.leap.entities.Account;
import com.neueda.leap.entities.Client;
import com.neueda.leap.models.PortfolioSummaryResponse;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.ClientRepository;
import com.neueda.leap.service.PortfolioObservationStore;
import com.neueda.leap.service.PortfolioSummaryService;
import com.neueda.leap.service.TokenService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:portfolio-history;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=false", "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:../database/transaction_schema.sql",
        "trading.execution.enabled=false", "portfolio.history.enabled=false", "spring.kafka.listener.auto-startup=false"
})
@AutoConfigureMockMvc
class PortfolioHistoryIntegrationTest {
    @Autowired ClientRepository clients;
    @Autowired AccountRepository accounts;
    @Autowired TokenService tokens;
    @Autowired PortfolioObservationStore observations;
    @Autowired MockMvc mvc;
    @MockitoBean PortfolioSummaryService summaries;

    private Account account() {
        var client = clients.save(new Client("History", UUID.randomUUID() + "@example.com", "hash"));
        return accounts.save(new Account(client, "History account"));
    }

    private PortfolioSummaryResponse summary(Account account, Instant time, String value, String profit) {
        return new PortfolioSummaryResponse(account.getAccountId(), "USD", new BigDecimal("100"),
                value == null ? null : new BigDecimal(value), null, new BigDecimal("200"),
                profit == null ? null : new BigDecimal(profit), null,
                profit == null ? "History does not reconcile." : null, time, time.minusSeconds(600), List.of(), List.of());
    }

    private String path(Account account) { return "/api/accounts/" + account.getAccountId() + "/portfolio-history"; }
    private String bearer(Account account) { return "Bearer " + tokens.issueToken(account.getClient()).accessToken(); }

    @Test void requiresAuthenticationAndNeverExposesAnotherClientsHistory() throws Exception {
        var owner = account();
        var stranger = account();
        mvc.perform(get(path(owner))).andExpect(status().isUnauthorized());
        mvc.perform(get(path(owner)).header("Authorization", bearer(stranger))).andExpect(status().isNotFound());
        verifyNoInteractions(summaries);
    }

    @Test void persistsChronologicalValuesAndExposesActualObservationAndQuoteTimes() throws Exception {
        var owner = account();
        Instant now = Instant.now();
        observations.record(summary(owner, now.minusSeconds(120), "300", "-20"));
        var latest = summary(owner, now, "340", "20");
        when(summaries.summary(owner.getAccountId(), owner.getClient().getClientId())).thenReturn(latest);
        mvc.perform(get(path(owner)).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.points.length()").value(2))
                .andExpect(jsonPath("$.points[0].portfolioValue").value(300))
                .andExpect(jsonPath("$.points[0].lifetimeProfitLoss").value(-20))
                .andExpect(jsonPath("$.points[1].portfolioValue").value(340))
                .andExpect(jsonPath("$.points[1].oldestQuoteAt").isNotEmpty());
        observations.record(latest);
        assertThat(observations.points(owner.getAccountId(), Instant.EPOCH)).hasSize(2);
    }

    @Test void filtersPeriodsWithoutResettingRecordingStartAndRejectsInvalidRanges() throws Exception {
        var owner = account();
        Instant old = Instant.now().minusSeconds(86400 * 10);
        observations.record(summary(owner, old, "100", "0"));
        when(summaries.summary(owner.getAccountId(), owner.getClient().getClientId()))
                .thenReturn(summary(owner, Instant.now(), "120", "20"));
        mvc.perform(get(path(owner)).param("range", "1D").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.points.length()").value(1))
                .andExpect(jsonPath("$.recordingStartedAt").isNotEmpty());
        mvc.perform(get(path(owner)).param("range", "invalid").header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest());
    }

    @Test void recordsMissingValuesAsGapsAndExcludesSyntheticPrices() {
        var owner = account();
        var incomplete = summary(owner, Instant.now().minusSeconds(120), null, null);
        observations.record(incomplete);
        var synthetic = summary(owner, Instant.now(), "300", "20");
        observations.record(new PortfolioSummaryResponse(synthetic.accountId(), "USD", synthetic.cashBalance(),
                synthetic.estimatedPortfolioValue(), null, synthetic.investedAssetsValue(), synthetic.lifetimeProfitLoss(),
                null, null, synthetic.accountAsOf(), synthetic.oldestQuoteAt(),
                List.of("Valuation includes synthetic provider quotes."), List.of()));
        var points = observations.points(owner.getAccountId(), Instant.EPOCH);
        assertThat(points).hasSize(2);
        assertThat(points).allSatisfy(point -> {
            assertThat(point.portfolioValue()).isNull();
            assertThat(point.lifetimeProfitLoss()).isNull();
        });
        assertThat(points.get(1).valueUnavailableReason()).contains("Synthetic");
    }

    @Test void doesNotOverwriteRecordedHistoryWithAnOlderConcurrentValuation() {
        var owner = account();
        Instant now = Instant.now();
        observations.record(summary(owner, now, "320", "20"));
        observations.record(summary(owner, now.minusSeconds(180), "300", "0"));
        var points = observations.points(owner.getAccountId(), Instant.EPOCH);
        assertThat(points).hasSize(1);
        assertThat(points.get(0).portfolioValue()).isEqualByComparingTo("320");
    }
}
