package com.neueda.leap.service;

import com.neueda.leap.repository.ClientRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

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

    @Test
    void provisionsClientWithNameFromAuthToken() {
        ClientRepository repository = mock(ClientRepository.class);
        ClientService clientService = new ClientService(repository);
        when(repository.findByEmailIgnoreCase("david@neueda.com")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("sub", "david@neueda.com")
                .claim("name", "David Smith")
                .claim("roles", List.of("analyst"))
                .build();

        Client client = clientService.findOrRegisterClientByJwt(jwt);

        assertEquals("David Smith", client.getClientName());
        verify(repository).save(any());
    }

    @Test
    void rejectsMissingNameRatherThanCreatingPlaceholderClient() {
        ClientRepository repository = mock(ClientRepository.class);
        ClientService clientService = new ClientService(repository);
        when(repository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("sub", "david@neueda.com")
                .claim("roles", List.of("analyst"))
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> clientService.findOrRegisterClientByJwt(jwt)
        );

        assertEquals("A user name is required to create a client profile.", exception.getMessage());
                verify(repository, never()).save(any());
    }
}