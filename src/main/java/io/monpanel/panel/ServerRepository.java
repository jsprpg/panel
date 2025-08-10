package io.monpanel.panel;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ServerRepository extends JpaRepository<Server, Long> {
    // Trouver tous les serveurs d'un utilisateur
    List<Server> findByOwner(User owner);
}