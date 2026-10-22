package com.neueda.leap.service;

import com.neueda.leap.entities.*;
import com.neueda.leap.enums.ClientStatus;
import com.neueda.leap.models.*;
import com.neueda.leap.repository.ClientRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class ClientServiceTest {
    private final ClientRepository repo = mock(ClientRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final TokenService tokens = mock(TokenService.class);
    private final ClientService service = new ClientService(repo, passwords, tokens);
    @Test void activeIdentityAndAdminLookupUseCurrentClientState() {
        for (String email : new String[]{null, "", " "}) {
            assertThat(service.findActiveClientIdByEmail(email)).isEmpty(); assertThat(service.findActiveClientByEmail(email)).isEmpty(); assertThat(service.isActiveAdmin(email)).isFalse();
        }
        verifyNoInteractions(repo); Client c = client(); when(repo.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(c));
        assertThat(service.findActiveClientIdByEmail(" alice@example.com ")).contains(7);
        assertThat(service.findActiveClientByEmail("alice@example.com")).contains(c); assertThat(service.isActiveAdmin("alice@example.com")).isFalse();
        id(c, "clientSegment", "admin"); assertThat(service.isActiveAdmin("alice@example.com")).isTrue();
        id(c, "clientStatus", ClientStatus.INACTIVE); assertThat(service.findActiveClientIdByEmail("alice@example.com")).isEmpty(); assertThat(service.isActiveAdmin("alice@example.com")).isFalse();
        assertThat(service.findActiveClientByEmail("unknown")).isEmpty();
    }
    @Test @Tag("legacy-auth") void registrationNormalizesIdentityAndEncodesPassword() {
        when(passwords.encode("password123")).thenReturn("encoded"); when(repo.save(any())).thenAnswer(c -> id(c.getArgument(0), "clientId", 7));
        when(tokens.issueToken(any())).thenReturn(new TokenService.TokenGrant("opaque", AT.plusDays(1)));
        var response = service.register(new RegisterRequest(" Alice ", " ALICE@EXAMPLE.COM ", "password123"));
        assertThat(response.email()).isEqualTo("alice@example.com"); assertThat(response.clientName()).isEqualTo("Alice"); assertThat(response.accessToken()).isEqualTo("opaque");
        verify(passwords).encode("password123");
        when(repo.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(client()));
        assertThatIllegalStateException().isThrownBy(() -> service.register(new RegisterRequest("Alice", "alice@example.com", "password123")));
    }
    @Test @Tag("legacy-auth") void requiredRegistrationFieldsRejectedWithoutIssuingToken() {
        for (RegisterRequest r : List.of(new RegisterRequest(null, "a@b", "password"), new RegisterRequest(" ", "a@b", "password"),
            new RegisterRequest("A", null, "password"), new RegisterRequest("A", " ", "password"), new RegisterRequest("A", "a@b", null), new RegisterRequest("A", "a@b", " ")))
            assertThatIllegalArgumentException().isThrownBy(() -> service.register(r));
        verifyNoInteractions(tokens, repo);
    }
    @Test @Tag("legacy-auth") void loginRejectsBadCredentialsAndInactiveUsersAndChecksAdmin() {
        var request = new SignInRequest("alice@example.com", "password");
        assertThatThrownBy(() -> service.signIn(request)).isInstanceOf(NoSuchElementException.class);
        Client c = client(); when(repo.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.signIn(request)).isInstanceOf(NoSuchElementException.class); verifyNoInteractions(tokens);
        when(passwords.matches("password", "hash")).thenReturn(true); when(tokens.issueToken(c)).thenReturn(new TokenService.TokenGrant("opaque", AT.plusDays(1)));
        assertThat(service.signIn(new SignInRequest(" ALICE@EXAMPLE.COM ", "password")).clientId()).isEqualTo(7);
        assertThatThrownBy(() -> service.signInAdmin(request)).isInstanceOf(NoSuchElementException.class);
        id(c, "clientSegment", "ADMIN"); assertThat(service.signInAdmin(request).clientSegment()).isEqualTo("ADMIN");
        id(c, "clientStatus", ClientStatus.INACTIVE); assertThatThrownBy(() -> service.signIn(request)).isInstanceOf(NoSuchElementException.class);
        for (SignInRequest r : List.of(new SignInRequest(null, "p"), new SignInRequest(" ", "p"), new SignInRequest("a", null), new SignInRequest("a", " ")))
            assertThatIllegalArgumentException().isThrownBy(() -> service.signIn(r));
    }
}
