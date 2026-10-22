package com.neueda.leap.service;

import com.neueda.leap.entities.Client;
import com.neueda.leap.enums.ClientStatus;
import com.neueda.leap.models.RegisterRequest;
import com.neueda.leap.models.SignInRequest;
import com.neueda.leap.models.AuthResponse;
import com.neueda.leap.repository.ClientRepository;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class ClientService {

    private final ClientRepository clientRepository;

    public ClientService(
            ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
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
    public boolean isActiveAdmin(Jwt jwt) {
        Object roles = jwt.getClaims().get("roles");
        if (roles == null || !(roles instanceof List<?>)){
            throw new IllegalArgumentException("Invalid access token");
        }

        boolean isAdmin = false;
        List<?> roleList = (List<?>) roles;
        for (Object role : roleList){
            if (role == null || !(role instanceof String)){
                throw new IllegalArgumentException("Invalid access token");
            }
            if (((String) role).toLowerCase() == "admin"){
                isAdmin = true;
                break;
            }
        }
        return isAdmin;
    }

    @Transactional
    public Client findOrRegisterClientByJwt(Jwt jwt){
        if (jwt == null){
            throw new IllegalArgumentException("An access token is required.");
        }

        String email = (String) jwt.getClaimAsString("sub");
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
}
