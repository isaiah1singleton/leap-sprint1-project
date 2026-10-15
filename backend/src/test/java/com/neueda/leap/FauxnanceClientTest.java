package com.neueda.leap;

import com.neueda.leap.market.FauxnanceClient;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FauxnanceClientTest {
    @Test
    void singleQuoteUsesQuotesPathUnderVersionedBaseUrl() {
        var builder = RestClient.builder().baseUrl("https://provider.test/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://provider.test/v1/quotes/AAPL"))
                .andRespond(withSuccess("""
                        {"data":{"symbol":"AAPL","price":100,"bid":99,"ask":101,
                        "currency":"USD","asOf":"2026-10-15T19:20:00Z"},
                        "meta":{"source":"cache","stale":false}}
                        """, MediaType.APPLICATION_JSON));
        var client = new FauxnanceClient(builder.build(), "test-key", Clock.systemUTC());
        assertThat(client.fetchQuote("AAPL").quote().price()).isEqualByComparingTo("100");
        server.verify();
    }
}
