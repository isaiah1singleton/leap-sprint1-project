package com.neueda.leap.service;

import com.neueda.leap.repository.ClientRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ClientServiceTest {

    private final ClientService service = new ClientService(mock(ClientRepository.class));

    @Test
    void recognizesAdminRoleFromJwtClaim() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("roles", List.of(new String("admin")))
                .build();

        assertTrue(service.isActiveAdmin(jwt));
    }

    @Test
    void doesNotTreatAnalystRoleAsAdmin() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("roles", List.of("analyst"))
                .build();

        assertFalse(service.isActiveAdmin(jwt));
    }

    @Test
    void doesNotProvisionAdminAsAClient() {
        ClientRepository repository = mock(ClientRepository.class);
        ClientService clientService = new ClientService(repository);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("sub", "admin@example.com")
                .claim("roles", List.of("admin"))
                .build();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> clientService.findOrRegisterClientByJwt(jwt)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(repository);
    }
}