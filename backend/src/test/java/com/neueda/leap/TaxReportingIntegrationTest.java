package com.neueda.leap;

import com.neueda.leap.entities.*;
import com.neueda.leap.enums.AssetClass;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.repository.*;
import com.neueda.leap.service.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tax-report;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=false", "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:../database/transaction_schema.sql",
        "trading.execution.enabled=false", "spring.kafka.listener.auto-startup=false"
})
@AutoConfigureMockMvc
@Transactional
class TaxReportingIntegrationTest {
    @Autowired TaxReportService reports;
    @Autowired TaxPdfGenerator pdf;
    @Autowired JdbcTemplate jdbc;
    @Autowired ClientRepository clients;
    @Autowired AccountRepository accounts;
    @Autowired InstrumentRepository instruments;
    @Autowired TokenService tokens;
    @Autowired MockMvc mvc;
    private Client client;
    private int account;
    private int equity;

    @BeforeEach void setup() {
        client = clients.save(new Client("Tax test", UUID.randomUUID() + "@example.com", "hash"));
        account = accounts.save(new Account(client, "Tax test")).getAccountId();
        equity = instrument(AssetClass.EQUITY, Currency.USD, "Test Stock");
    }

    @Test void profitableSaleUsesExecutionPricesAndAcquisitionDates() {
        int buy = fill(account, equity, "BUY", "5", "10", "2024-01-01T12:00:00Z");
        int sell = fill(account, equity, "SELL", "2", "15", "2026-01-02T12:00:00Z");
        allocate(sell, buy, "2");
        var report = reports.report(client.getClientId(), 2026, "1099-B");
        assertThat(report.sales()).hasSize(1);
        var sale = report.sales().get(0);
        assertThat(sale.proceeds()).isEqualByComparingTo("30");
        assertThat(sale.basis()).isEqualByComparingTo("20");
        assertThat(report.net()).isEqualByComparingTo("10");
        assertThat(sale.lots().get(0).acquired()).hasToString("2024-01-01");
        assertThat(sale.lots().get(0).holdingPeriod()).isEqualTo("Long-term");
    }

    @Test void multipleFifoLotsCombineBasisAndKeepDistinctHoldingPeriods() {
        int first = fill(account, equity, "BUY", "2", "10", "2024-01-01T00:00:00Z");
        int second = fill(account, equity, "BUY", "3", "20", "2026-01-01T00:00:00Z");
        int sell = fill(account, equity, "SELL", "4", "30", "2026-02-01T00:00:00Z");
        allocate(sell, first, "2");
        allocate(sell, second, "2");
        var sale = reports.report(client.getClientId(), 2026, "1099-B").sales().get(0);
        assertThat(sale.basis()).isEqualByComparingTo("60");
        assertThat(sale.gain()).isEqualByComparingTo("60");
        assertThat(sale.lots()).extracting(lot -> lot.holdingPeriod()).containsExactly("Long-term", "Short-term");
        assertThat(sale.lots().stream().map(lot -> lot.proceeds()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(sale.proceeds());
    }

    @Test void etfLossAndExactlyOneYearHoldingAreReported() {
        int etf = instrument(AssetClass.ETF, Currency.USD, "Test ETF");
        int buy = fill(account, etf, "BUY", "3", "20", "2025-02-01T00:00:00Z");
        int sell = fill(account, etf, "SELL", "3", "12", "2026-02-01T00:00:00Z");
        allocate(sell, buy, "3");
        var report = reports.report(client.getClientId(), 2026, "1099-B");
        assertThat(report.losses()).isEqualByComparingTo("24");
        assertThat(report.gains()).isZero();
        assertThat(report.net()).isEqualByComparingTo("-24");
        assertThat(report.sales().get(0).lots().get(0).holdingPeriod()).isEqualTo("Short-term");
    }

    @Test void fractionalUsdCryptoUsesSameServiceAndIsSeparatedFromSecurities() {
        int crypto = instrument(AssetClass.CRYPTO, Currency.USD, "Test Bitcoin");
        int buy = fill(account, crypto, "BUY", "0.01234567", "40000.12345678", "2025-12-01T00:00:00Z");
        int sell = fill(account, crypto, "SELL", "0.00000001", "60000.12345678", "2026-02-01T00:00:00Z");
        allocate(sell, buy, "0.00000001");
        int foreign = instrument(AssetClass.CRYPTO, Currency.EUR, "Unsupported pair");
        fill(account, foreign, "SELL", "1", "20", "2026-02-01T00:00:00Z");
        var report = reports.report(client.getClientId(), 2026, "1099-DA");
        assertThat(report.sales()).hasSize(1);
        assertThat(report.sales().get(0).quantity()).isEqualByComparingTo("0.00000001");
        assertThat(report.sales().get(0).basis()).isEqualByComparingTo("0.00040000");
        assertThat(report.net()).isEqualByComparingTo("0.00020000");
        assertThat(reports.report(client.getClientId(), 2026, "1099-B").sales()).isEmpty();
    }

    @Test void securitiesRetainTheirCurrenciesWithoutInventingConversionRates() {
        int euroStock = instrument(AssetClass.EQUITY, Currency.EUR, "Euro stock");
        int buy = fill(account, euroStock, "BUY", "1", "10", "2025-01-01T00:00:00Z");
        allocate(fill(account, euroStock, "SELL", "1", "15", "2026-02-01T00:00:00Z"), buy, "1");
        var euro = reports.report(client.getClientId(), 2026, "1099-B");
        assertThat(euro.currency()).isEqualTo("EUR");
        assertThat(euro.net()).isEqualByComparingTo("5");
        int dollarBuy = fill(account, equity, "BUY", "1", "20", "2025-01-01T00:00:00Z");
        allocate(fill(account, equity, "SELL", "1", "18", "2026-02-01T00:00:00Z"), dollarBuy, "1");
        var mixed = reports.report(client.getClientId(), 2026, "1099-B");
        assertThat(mixed.sales()).hasSize(2);
        assertThat(mixed.currency()).isEqualTo("Mixed");
        assertThat(mixed.net()).isNull();
        assertThat(mixed.sales()).extracting(sale -> sale.currency()).containsExactly("EUR", "USD");
    }

    @Test void fractionalLotRoundingReconcilesToSaleWithoutNegativeProceeds() {
        int sell = fill(account, equity, "SELL", "0.00000005", "0.6", "2026-02-01T00:00:00Z");
        for (int i = 0; i < 5; i++) {
            int buy = fill(account, equity, "BUY", "0.00000001", "1", "2025-01-01T00:00:00Z");
            allocate(sell, buy, "0.00000001");
        }
        var sale = reports.report(client.getClientId(), 2026, "1099-B").sales().get(0);
        assertThat(sale.lots()).allSatisfy(lot -> assertThat(lot.proceeds()).isNotNegative());
        assertThat(sale.lots().stream().map(lot -> lot.proceeds()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("0.00000003").isEqualByComparingTo(sale.proceeds());
    }

    @Test void noSalesAndUnfilledOrRejectedOrdersDoNotCreateDispositions() {
        fill(account, equity, "BUY", "10", "10", "2026-01-01T00:00:00Z");
        int order = order(account, equity, "SELL", "1", "2026-02-01T00:00:00Z");
        jdbc.update("INSERT INTO order_events (order_id,status,reason,occurred_at) VALUES (?,'REJECTED','Test rejection',?)",
                order, OffsetDateTime.parse("2026-02-01T00:00:00Z"));
        var report = reports.report(client.getClientId(), 2026, "1099-B");
        assertThat(report.sales()).isEmpty();
        assertThat(report.net()).isZero();
    }

    @Test void yearUsesUtcSaleTimeAndIncludesPriorYearAcquisitions() {
        int buy = fill(account, equity, "BUY", "10", "10", "2023-01-01T00:00:00Z");
        for (String time : List.of("2025-12-31T23:59:59Z", "2026-01-01T01:00:00+01:00",
                "2026-12-31T23:59:59Z", "2027-01-01T00:00:00Z")) {
            allocate(fill(account, equity, "SELL", "1", "20", time), buy, "1");
        }
        var report = reports.report(client.getClientId(), 2026, "1099-B");
        assertThat(report.sales()).hasSize(2);
        assertThat(report.net()).isEqualByComparingTo("20");
        assertThatThrownBy(() -> reports.report(client.getClientId(), 1899, "1099-B"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void missingOrInconsistentBasisIsUnavailableInsteadOfInvented() {
        fill(account, equity, "SELL", "1", "20", "2026-02-01T00:00:00Z");
        int buy = fill(account, equity, "BUY", "1", "10", "2025-01-01T00:00:00Z");
        int partial = fill(account, equity, "SELL", "2", "20", "2026-02-01T00:00:00Z");
        allocate(partial, buy, "1");
        var report = reports.report(client.getClientId(), 2026, "1099-B");
        assertThat(report.sales()).hasSize(2).allSatisfy(sale -> {
            assertThat(sale.basis()).isNull();
            assertThat(sale.gain()).isNull();
            assertThat(sale.lots()).isEmpty();
        });
        assertThat(report.unavailableBasisCount()).isEqualTo(2);
        assertThat(report.net()).isZero();
    }

    @Test void endpointsRequireAuthenticationAndNeverAcceptAnotherUsersIdentity() throws Exception {
        Client other = clients.save(new Client("Other", UUID.randomUUID() + "@example.com", "hash"));
        int otherAccount = accounts.save(new Account(other, "Other")).getAccountId();
        fill(otherAccount, equity, "SELL", "999", "100", "2026-02-01T00:00:00Z");
        String token = tokens.issueToken(client).accessToken();
        for (String route : List.of(ApiRoutes.TAX + ApiRoutes.TAX_REPORT,
                ApiRoutes.TAX + "/documents/1099-B", ApiRoutes.TAX + "/documents/1099-DA")) {
            mvc.perform(get(route)).andExpect(status().isUnauthorized());
            mvc.perform(get(route).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        }
        mvc.perform(get(ApiRoutes.TAX + ApiRoutes.TAX_REPORT).param("year", "2026")
                        .param("clientId", other.getClientId().toString()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sales").isEmpty())
                .andExpect(header().string("Cache-Control", "no-store"));
        for (String form : List.of("1099-B", "1099-DA")) {
            var response = mvc.perform(get(ApiRoutes.TAX + "/documents/" + form).param("year", "2026")
                            .param("clientId", other.getClientId().toString()).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                    .andExpect(header().string("Content-Disposition",
                            "attachment; filename=\"2026-Simulated-" + form + ".pdf\""))
                    .andReturn().getResponse().getContentAsByteArray();
            try (var doc = Loader.loadPDF(response)) {
                assertThat(new PDFTextStripper().getText(doc)).contains("No completed dispositions").doesNotContain("999");
            }
        }
        mvc.perform(get(ApiRoutes.TAX + "/documents/invalid").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test void bothPdfsContainCalculatedDataAndPaginateWithRepeatedLabels() throws Exception {
        for (var asset : List.of(AssetClass.EQUITY, AssetClass.CRYPTO)) {
            String form = asset == AssetClass.EQUITY ? "1099-B" : "1099-DA";
            int instrument = instrument(asset, Currency.USD, "PDF test asset");
            int buy = fill(account, instrument, "BUY", "100", "10", "2025-01-01T00:00:00Z");
            for (int i = 0; i < 16; i++) {
                allocate(fill(account, instrument, "SELL", "1", "15", "2026-02-01T00:00:00Z"), buy, "1");
            }
            var report = reports.report(client.getClientId(), 2026, form);
            byte[] bytes = pdf.generate(report);
            try (var doc = Loader.loadPDF(bytes)) {
                assertThat(doc.getNumberOfPages()).isGreaterThan(1);
                String text = new PDFTextStripper().getText(doc);
                assertThat(text).contains("Simulated Form " + form, "PDF test asset", "2025-01-01",
                        "Realized gains: 80 USD", "Long-term", "not an IRS information return");
                for (int page = 1; page <= doc.getNumberOfPages(); page++) {
                    var reader = new PDFTextStripper();
                    reader.setStartPage(page);
                    reader.setEndPage(page);
                    assertThat(reader.getText(doc)).contains(TaxPdfGenerator.DISCLAIMER, "Page " + page);
                }
                // Test-only previews for visual inspection, never used as application data.
                Path directory = Path.of("target", "tax-preview");
                Files.createDirectories(directory);
                Files.write(directory.resolve(form + ".pdf"), bytes);
                for (int page = 0; page < doc.getNumberOfPages(); page++) {
                    ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(page, 110), "png",
                            directory.resolve(form + "-" + (page + 1) + ".png").toFile());
                }
            }
        }
    }

    private int instrument(AssetClass asset, Currency currency, String name) {
        return instruments.save(new Instrument("US", UUID.randomUUID().toString().substring(0, 8),
                name, asset, currency)).getInstrumentId();
    }
    private int order(int owner, int instrument, String side, String quantity, String time) {
        jdbc.update("""
                INSERT INTO orders (account_id,instrument_id,side,requested_quantity,
                submitted_quote_price,submitted_quote_at,submitted_at) VALUES (?,?,?,?,999,?,?)
                """, owner, instrument, side, new BigDecimal(quantity), OffsetDateTime.parse(time), OffsetDateTime.parse(time));
        return jdbc.queryForObject("SELECT MAX(order_id) FROM orders WHERE account_id=?", Integer.class, owner);
    }
    private int fill(int owner, int instrument, String side, String quantity, String price, String time) {
        int order = order(owner, instrument, side, quantity, time);
        jdbc.update("INSERT INTO fills (order_id,execution_price,execution_time) VALUES (?,?,?)",
                order, new BigDecimal(price), OffsetDateTime.parse(time));
        return jdbc.queryForObject("SELECT fill_id FROM fills WHERE order_id=?", Integer.class, order);
    }
    private void allocate(int sell, int buy, String quantity) {
        jdbc.update("INSERT INTO fifo_allocations (sell_fill_id,buy_fill_id,allocated_quantity) VALUES (?,?,?)",
                sell, buy, new BigDecimal(quantity));
    }
}
