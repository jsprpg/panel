package io.monpanel.panel;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    // Méthode pour trouver un utilisateur par son nom
    Optional<User> findByUsername(String username);
}