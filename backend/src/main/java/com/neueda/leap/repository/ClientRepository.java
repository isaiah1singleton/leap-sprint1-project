package com.neueda.leap.repository;

import com.neueda.leap.entities.Client;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Integer> {
    Optional<Client> findByEmailIgnoreCase(String email);
}
