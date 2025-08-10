package io.monpanel.panel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/login")
    public String login() {
        return "login"; // Affiche la page login.html
    }

    @GetMapping("/register")
    public String showRegistrationForm() {
        return "register"; // Affiche la page register.html
    }

    @PostMapping("/register")
    public String registerUser(@ModelAttribute User user) {
        // Hacher le mot de passe avant de l'enregistrer
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole("ROLE_USER");
        userRepository.save(user);
        return "redirect:/login"; // Redirige vers la page de connexion après l'inscription
    }
}