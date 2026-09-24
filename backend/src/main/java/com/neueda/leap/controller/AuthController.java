package com.neueda.leap.controller;

import com.neueda.leap.entities.Client;
import com.neueda.leap.models.AuthRequest;
import com.neueda.leap.models.AuthResponse;
import com.neueda.leap.repository.ClientRepository;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"})
public class AuthController {

    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(ClientRepository clientRepository, PasswordEncoder passwordEncoder) {
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest request) {
        String email = normalizedEmail(request);
        if (email == null || request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().body("Email and password are required.");
        }
        if (clientRepository.findByEmailIgnoreCase(email).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("An account with this email already exists.");
        }

        Client client = clientRepository.save(new Client(email, passwordEncoder.encode(request.password())));
        return ResponseEntity.status(HttpStatus.CREATED).body(response(client));
    }

    @PostMapping("/sign-in")
    public ResponseEntity<?> signIn(@RequestBody AuthRequest request) {
        String email = normalizedEmail(request);
        if (email == null || request.password() == null) {
            return ResponseEntity.badRequest().body("Email and password are required.");
        }

        return clientRepository.findByEmailIgnoreCase(email)
                .filter(client -> passwordEncoder.matches(request.password(), client.getPasswordHash()))
                .<ResponseEntity<?>>map(client -> ResponseEntity.ok(response(client)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password."));
    }

    private String normalizedEmail(AuthRequest request) {
        if (request == null || request.email() == null || request.email().isBlank()) {
            return null;
        }
        return request.email().trim().toLowerCase(Locale.ROOT);
    }

    private AuthResponse response(Client client) {
        return new AuthResponse(client.getClientId(), client.getEmail(), client.getClientSegment());
    }
}
