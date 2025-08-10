package io.monpanel.panel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // La seule responsabilité de ce fichier est de créer l'admin si besoin.
        if (userRepository.count() == 0) {
            log.info("Aucun utilisateur trouvé. Création du compte administrateur par défaut...");
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRole("ROLE_ADMIN");
            userRepository.save(admin);
            log.info("Compte administrateur créé avec succès !");
        } else {
            log.info("Des utilisateurs existent déjà. Le compte admin par défaut n'a pas été créé.");
        }
    }
}