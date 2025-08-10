package io.monpanel.panel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ServerController {

    @Autowired
    private ServerRepository serverRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EggService eggService; // Remplacement de GameEggRepository
    @Autowired
    private DockerService dockerService;

    @GetMapping("/servers")
    public String listServers(Model model, Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName()).get();
        model.addAttribute("servers", serverRepository.findByOwner(currentUser));
        return "servers";
    }

    @GetMapping("/servers/new")
    public String showCreateServerForm(Model model) {
        model.addAttribute("nests", eggService.getGroupedEggs());
        return "create-server";
    }
    
    @PostMapping("/servers/create")
    public String createServer(Authentication authentication,
                               @RequestParam String serverName,
                               @RequestParam int serverPort,
                               @RequestParam String dockerImage,
                               @RequestParam int memory,
                               @RequestParam double cpu,
                               @RequestParam int disk,
                               RedirectAttributes redirectAttributes) {
                                   
        User currentUser = userRepository.findByUsername(authentication.getName()).get();
        
        Server newServer = new Server();
        newServer.setName(serverName);
        newServer.setHostPort(serverPort);
        newServer.setDockerImage(dockerImage);
        newServer.setOwner(currentUser);
        newServer.setMemory(memory);
        newServer.setCpu(cpu);
        newServer.setDisk(disk);
        try {
            String containerId = dockerService.createMinecraftServer(newServer);
            newServer.setContainerId(containerId);
            serverRepository.save(newServer);
            redirectAttributes.addFlashAttribute("successMessage", "Le serveur '" + serverName + "' a été créé avec succès !");
        } catch (Exception e) {
            System.err.println("ERREUR LORS DE LA CRÉATION DU SERVEUR : " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Impossible de créer le serveur : " + e.getMessage());
        }
        return "redirect:/servers";
    }

    @PostMapping("/servers/delete/{id}")
    public String deleteServer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Server serverToDelete = serverRepository.findById(id).orElse(null);
        if (serverToDelete == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Serveur introuvable.");
            return "redirect:/servers";
        }
        try {
            dockerService.deleteServerContainer(serverToDelete.getContainerId());
            serverRepository.delete(serverToDelete);
            redirectAttributes.addFlashAttribute("successMessage", "Le serveur '" + serverToDelete.getName() + "' a été supprimé avec succès.");
        } catch (Exception e) {
            System.err.println("ERREUR LORS DE LA SUPPRESSION DU SERVEUR : " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Impossible de supprimer le serveur : " + e.getMessage());
        }
        return "redirect:/servers";
    }

    @PostMapping("/servers/force-delete/{id}")
    public String forceDeleteServer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Server serverToForceDelete = serverRepository.findById(id).orElse(null);
        if (serverToForceDelete == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Serveur introuvable.");
            return "redirect:/servers";
        }
        try {
            dockerService.deleteServerContainer(serverToForceDelete.getContainerId());
        } catch (Exception e) {
            System.err.println("AVERTISSEMENT : Échec de la suppression du conteneur Docker lors d'une suppression forcée. Erreur ignorée : " + e.getMessage());
        }
        serverRepository.delete(serverToForceDelete);
        redirectAttributes.addFlashAttribute("successMessage", "Le serveur '" + serverToForceDelete.getName() + "' a été retiré du panel (suppression forcée).");
        return "redirect:/servers";
    }

    @GetMapping("/server/{id}")
    public String viewServer(@PathVariable Long id, Model model) {
        Server server = serverRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid server Id:" + id));
        
        ServerStats initialStats = dockerService.getStats(server.getContainerId());

        model.addAttribute("server", server);
        model.addAttribute("initialStats", initialStats);

        return "view-server";
    }

    @PostMapping("/server/{id}/action")
    public String handleServerAction(@PathVariable Long id, @RequestParam String action, RedirectAttributes redirectAttributes) {
        Server server = serverRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid server Id:" + id));
        try {
            dockerService.performServerAction(server.getContainerId(), action);
            redirectAttributes.addFlashAttribute("successMessage", "Action '" + action + "' exécutée avec succès.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Échec de l'action '" + action + "': " + e.getMessage());
        }
        return "redirect:/server/" + id;
    }
    
    @GetMapping("/server/{id}/files")
    public String filesPage(@PathVariable Long id, Model model) {
        Server server = serverRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid server Id:" + id));
        model.addAttribute("server", server);
        return "files";
    }
}