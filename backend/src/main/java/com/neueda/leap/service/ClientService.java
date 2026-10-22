package com.neueda.leap.service;

import com.neueda.leap.entities.Client;
import com.neueda.leap.enums.ClientStatus;
import com.neueda.leap.models.RegisterRequest;
import com.neueda.leap.models.SignInRequest;
import com.neueda.leap.models.AuthResponse;
import com.neueda.leap.repository.ClientRepository;
import jakarta.transaction.Transactional;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public ClientService(
            ClientRepository clientRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService) {
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    // to-do: remove this function
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String name = request == null || request.name() == null ? null : request.name().trim();
        String email = normalizedEmail(request.email());

        if (name == null || name.isBlank() || email == null) {
            throw new IllegalArgumentException("Name, email and password are required.");
        }

        if (clientRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new IllegalStateException("An account with this email already exists.");
        }

        Client client = clientRepository.save(
                new Client(name, email, "temp")
        );

        return toResponseWithToken(client);
    }


    @Transactional
    public Client registerClient(RegisterRequest request) {
        String name = request == null || request.name() == null ? null : request.name().trim();
        String email = normalizedEmail(request.email());

        if (name == null || name.isBlank() || email == null) {
            throw new IllegalArgumentException("Name, email and password are required.");
        }

        if (clientRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new IllegalStateException("An account with this email already exists.");
        }

        Client client = clientRepository.save(
                new Client(name, email, "temp")
        );

        return client;
    }

    // to-do: remove this function
    @Transactional
    public AuthResponse signIn(SignInRequest request) {
        String email = normalizedEmail(request.email());
        String password = request == null ? null : request.password();

        if (email == null || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Email and password are required.");
        }

        Client client = clientRepository.findByEmailIgnoreCase(email)
                .filter(found -> found.getClientStatus() == ClientStatus.ACTIVE
                        && passwordEncoder.matches(password, found.getPasswordHash()))
                .orElseThrow(() -> new NoSuchElementException("Invalid email or password."));

        return toResponseWithToken(client);
    }

    @Transactional
    public AuthResponse signInAdmin(SignInRequest request) {
        AuthResponse response = signIn(request);
        if (!"ADMIN".equalsIgnoreCase(response.clientSegment())) {
            throw new NoSuchElementException("Administrator access is required.");
        }
        return response;
    }

    @Transactional
    public Optional<Integer> findActiveClientIdByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        return clientRepository.findByEmailIgnoreCase(email.trim())
                .filter(client -> client.getClientStatus() == ClientStatus.ACTIVE)
                .map(Client::getClientId);
    }

    @Transactional
    public Optional<Client> findActiveClientByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        return clientRepository.findByEmailIgnoreCase(email.trim())
                .filter(client -> client.getClientStatus() == ClientStatus.ACTIVE);
    }

    @Transactional
    public boolean isActiveAdmin(String email) {
        return findActiveClientByEmail(email)
                .map(client -> "ADMIN".equalsIgnoreCase(client.getClientSegment()))
                .orElse(false);
    }

    @Transactional
    public Client findOrRegisterClientByJwt(Jwt jwt){
        if (jwt == null){
            throw new IllegalArgumentException("An access token is required.");
        }

        String email = jwt.getClaims().get("sub").toString();
        Optional<Client> clientOpt = findActiveClientByEmail(email);

        Client client;
        if (clientOpt.isEmpty()){
            // register new client
            // to-do add name to token and register here
            RegisterRequest request = new RegisterRequest("John Doe", email);
            client = registerClient(request);
        }
        else{
            // safe to cast Optional<Client> to Client
            client = clientOpt.get();
        }

        return client;
    }

    private String normalizedEmail(String emailInput) {
        if (emailInput == null || emailInput.isBlank()) {
            return null;
        }
        return emailInput.trim().toLowerCase(Locale.ROOT);
    }

    private AuthResponse toResponseWithToken(Client client) {
        TokenService.TokenGrant token = tokenService.issueToken(client);
        return new AuthResponse(
                client.getClientId(),
                client.getClientName(),
                client.getEmail(),
                client.getClientSegment(),
                token.accessToken(),
                "Bearer",
                token.expiresAt()
        );
    }
}
