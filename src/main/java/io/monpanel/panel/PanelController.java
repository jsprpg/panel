package io.monpanel.panel;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PanelController {

    /**
     * Affiche la page d'accueil principale après la connexion.
     * Accessible à tous les utilisateurs authentifiés.
     * @return Le nom du template "index.html".
     */
    @GetMapping("/")
    public String showHomePage() {
        return "index";
    }

    /**
     * Affiche la page du panel d'administration.
     * L'accès est restreint par Spring Security au rôle "ADMIN".
     * @return Le nom du template "admin.html".
     */
    @GetMapping("/admin")
    public String showAdminPage() {
        return "admin";
    }

}