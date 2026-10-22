package com.neueda.leap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neueda.leap.entities.Account;
import com.neueda.leap.entities.Client;
import com.neueda.leap.entities.Instrument;
import com.neueda.leap.entities.Order;
import com.neueda.leap.enums.AssetClass;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.AuthSessionRepository;
import com.neueda.leap.repository.ClientRepository;
import com.neueda.leap.repository.InstrumentRepository;
import com.neueda.leap.repository.OrderRepository;
import com.neueda.leap.service.TokenService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-submission;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:../database/transaction_schema.sql"
})
@AutoConfigureMockMvc
class OrderSubmissionIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private ClientRepository clients;
    @Autowired private AuthSessionRepository sessions;
    @Autowired private AccountRepository accounts;
    @Autowired private InstrumentRepository instruments;
    @Autowired private OrderRepository orders;

    @BeforeEach
    void clearDatabase() {
        orders.deleteAll();
        accounts.deleteAll();
        instruments.deleteAll();
        sessions.deleteAll();
        clients.deleteAll();
    }

    @Test
    void authenticatedOwnerCanSubmitAnOrderThatSatisfiesTheSchema() throws Exception {
        Client client = createClient();
        Account account = accounts.save(new Account(client, "Trading"));
        Instrument instrument = createInstrument();

        mockMvc.perform(post(ApiRoutes.ORDER)
                        .header("Authorization", bearer(client))
                        .contentType("application/json")
                        .content(orderJson(account.getAccountId(), instrument.getInstrumentId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").isNumber());

        assertThat(orders.count()).isEqualTo(1);
        Order saved = orders.findAll().get(0); 
        assertThat(saved.getSubmittedQuoteAt()).isEqualTo(saved.getSubmittedAt());
        assertThat(saved.getRequestedQuantity()).isEqualByComparingTo("2");
    }

    @Test
    void anotherClientCannotSubmitAgainstSomeoneElsesAccount() throws Exception {
        Client owner = createClient();
        Client otherClient = createClient();
        Account account = accounts.save(new Account(owner, "Trading"));
        Instrument instrument = createInstrument();

        mockMvc.perform(post(ApiRoutes.ORDER)
                        .header("Authorization", bearer(otherClient))
                        .contentType("application/json")
                        .content(orderJson(account.getAccountId(), instrument.getInstrumentId())))
                .andExpect(status().isNotFound());
        assertThat(orders.count()).isZero();
    }

    @Test
    void requestErrorsAreNotMaskedAsAuthenticationFailures() throws Exception {
        Client client = createClient();
        String authorization = bearer(client);

        mockMvc.perform(post(ApiRoutes.ORDER)
                        .contentType("application/json")
                        .content("not-json"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(ApiRoutes.ORDER)
                        .header("Authorization", authorization)
                        .contentType("application/json")
                        .content("not-json"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(ApiRoutes.ORDER)
                        .header("Authorization", authorization)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/orders")
                        .header("Authorization", authorization)
                        .contentType("application/json")
                        .content("not-json"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/error").header("Authorization", authorization)
                        .with(request -> {
                            request.setDispatcherType(DispatcherType.ERROR);
                            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, ApiRoutes.ORDER);
                            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 400);
                            return request;
                        }))
                .andExpect(status().isBadRequest());
    }

    private Client createClient() {
        return clients.save(new Client("Test", UUID.randomUUID() + "@example.com", "unused"));
    }

    private Instrument createInstrument() {
        return instruments.save(new Instrument("NYSE", UUID.randomUUID().toString().substring(0, 8),
                "Test instrument", AssetClass.EQUITY, Currency.USD));
    }

    private String bearer(Client client) {
        return "Bearer " + tokenService.issueToken(client).accessToken();
    }

    private static String orderJson(Integer accountId, Integer instrumentId) {
        return "{\"accountId\":" + accountId + ",\"instrumentId\":" + instrumentId
                + ",\"side\":\"BUY\",\"requestedQuantity\":2,\"submittedQuotePrice\":1}";
    }
}
