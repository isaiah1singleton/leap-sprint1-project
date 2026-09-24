package com.neueda.leap.service;

import com.neueda.leap.entities.Client;
import com.neueda.leap.enums.ClientStatus;
import com.neueda.leap.models.AuthRequest;
import com.neueda.leap.models.AuthResponse;
import com.neueda.leap.repository.ClientRepository;
import jakarta.transaction.Transactional;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    public ClientService(ClientRepository clientRepository, PasswordEncoder passwordEncoder) {
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthResponse register(AuthRequest request) {
        String email = normalizedEmail(request);
        String password = request == null ? null : request.password();

        if (email == null || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Email and password are required.");
        }

        if (clientRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new IllegalStateException("An account with this email already exists.");
        }

        Client client = clientRepository.save(
                new Client(email, passwordEncoder.encode(password))
        );

        return toResponse(client);
    }

    @Transactional
    public AuthResponse signIn(AuthRequest request) {
        String email = normalizedEmail(request);
        String password = request == null ? null : request.password();

        if (email == null || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Email and password are required.");
        }

        Client client = clientRepository.findByEmailIgnoreCase(email)
                .filter(found -> passwordEncoder.matches(password, found.getPasswordHash()))
                .orElseThrow(() -> new NoSuchElementException("Invalid email or password."));

        return toResponse(client);
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

    private String normalizedEmail(AuthRequest request) {
        if (request == null || request.email() == null || request.email().isBlank()) {
            return null;
        }
        return request.email().trim().toLowerCase(Locale.ROOT);
    }

    private AuthResponse toResponse(Client client) {
        return new AuthResponse(
                client.getClientId(),
                client.getEmail(),
                client.getClientSegment()
        );
    }
}
