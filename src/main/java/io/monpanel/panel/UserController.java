package io.monpanel.panel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/change-password")
    public String showChangePasswordForm() {
        return "change-password"; // Affiche la page HTML
    }

    @PostMapping("/change-password")
    public String changePassword(Authentication authentication,
                                 @RequestParam String oldPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes redirectAttributes) {

        // Récupérer l'utilisateur actuellement connecté
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User currentUser = userRepository.findByUsername(userDetails.getUsername()).orElse(null);

        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Utilisateur non trouvé.");
            return "redirect:/change-password";
        }

        // 1. Vérifier si l'ancien mot de passe est correct
        if (!passwordEncoder.matches(oldPassword, currentUser.getPassword())) {
            redirectAttributes.addFlashAttribute("errorMessage", "L'ancien mot de passe est incorrect.");
            return "redirect:/change-password";
        }

        // 2. Vérifier si les nouveaux mots de passe correspondent
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Les nouveaux mots de passe ne correspondent pas.");
            return "redirect:/change-password";
        }

        // 3. Mettre à jour le mot de passe
        currentUser.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(currentUser);

        redirectAttributes.addFlashAttribute("successMessage", "Votre mot de passe a été mis à jour avec succès !");
        return "redirect:/change-password";
    }
}