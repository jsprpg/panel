package io.monpanel.panel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class DockerService {

    private static final Logger log = LoggerFactory.getLogger(DockerService.class);

    // ===================================================================
    // MÉTHODE DÉDIÉE ET FIABLE POUR MINECRAFT (RETOUR À CE QUI MARCHAIT)
    // ===================================================================
    public String createMinecraftServer(Server server, GameEgg egg) throws Exception {
        log.info("Création d'un serveur Minecraft avec la méthode dédiée.");

        Path hostPath = setupServerDirectories(server);
        List<String> command = new ArrayList<>(List.of(
            "docker", "run", "-d",
            "--name", generateContainerName(server.getName()),
            "--memory", server.getMemory() + "m",
            "--cpus", String.valueOf(server.getCpu()),
            "-v", hostPath.toAbsolutePath().toString() + ":/data",
            "-p", server.getHostPort() + ":" + server.getHostPort(),
            // Ajout explicite et forcé des variables d'environnement
            "-e", "EULA=TRUE"
        ));
        
        // Ajoute les variables d'environnement de l'Egg si elles existent
        if (egg.getEnvironment() != null) {
            for (Map.Entry<String, String> entry : egg.getEnvironment().entrySet()) {
                // On s'assure de ne pas dupliquer EULA
                if (!"EULA".equalsIgnoreCase(entry.getKey())) {
                    command.add("-e");
                    command.add(entry.getKey() + "=" + entry.getValue());
                }
            }
        }
        
        command.add(server.getDockerImage());

        return executeDockerCommand(command);
    }

    // ===================================================================
    // MÉTHODE GÉNÉRIQUE POUR LES AUTRES SERVICES (LLM, etc.)
    // ===================================================================
    public String createGenericServer(Server server, GameEgg egg) throws Exception {
        log.info("Création d'un serveur générique (non-Minecraft).");
        
        Path hostPath = setupServerDirectories(server);
        List<String> command = new ArrayList<>(List.of(
            "docker", "run", "-d",
            "--name", generateContainerName(server.getName()),
            "--memory", server.getMemory() + "m",
            "--cpus", String.valueOf(server.getCpu()),
            "-v", hostPath.toAbsolutePath().toString() + ":/data"
        ));
        
        if (egg.getPorts() != null) {
            for (Map.Entry<String, String> entry : egg.getPorts().entrySet()) {
                command.add("-p");
                command.add(entry.getKey() + ":" + entry.getValue());
            }
        }
        
        if (egg.getEnvironment() != null) {
            for (Map.Entry<String, String> entry : egg.getEnvironment().entrySet()) {
                command.add("-e");
                command.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        
        command.add(server.getDockerImage());
        
        return executeDockerCommand(command);
    }


    // --- Fonctions utilitaires ---

    private Path setupServerDirectories(Server server) throws IOException {
        String serverUuid = UUID.randomUUID().toString();
        Path hostPath = Paths.get("servers", serverUuid);
        Files.createDirectories(hostPath);
        server.setHostPath(hostPath.toAbsolutePath().toString());
        return hostPath;
    }
    
    private String generateContainerName(String serverName) {
        return serverName.replaceAll("[^a-zA-Z0-9_.-]", "_") + "_" + System.currentTimeMillis();
    }

    private String executeDockerCommand(List<String> command) throws Exception {
        log.info("Commande Docker complète en cours d'exécution : {}", String.join(" ", command));
        
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();
            
            // On attend que la commande "docker run -d" se termine, ce qui est quasi instantané.
            process.waitFor();
            
            // On récupère l'ID du conteneur via une commande 'inspect' car 'run -d' ne retourne que l'ID long
            String containerName = command.get(4); // L'index 4 est le nom du conteneur que nous avons généré
            Process inspectProcess = new ProcessBuilder("docker", "inspect", "--format", "{{.Id}}", containerName).start();
            String containerId = new BufferedReader(new InputStreamReader(inspectProcess.getInputStream())).readLine();
            
            if (containerId == null || containerId.isBlank()) {
                // Lecture de la sortie d'erreur du processus initial en cas de problème
                String error = new BufferedReader(new InputStreamReader(process.getErrorStream())).lines().collect(java.util.stream.Collectors.joining("\n"));
                log.error("Erreur Docker : {}", error);
                throw new RuntimeException("N'a pas pu récupérer l'ID du conteneur après sa création. Erreur : " + error);
            }

            log.info("Conteneur créé avec succès ! ID : {}", containerId);
            return containerId.substring(0, 12);

        } catch (Exception e) {
            log.error("Impossible d'exécuter la commande Docker.", e);
            throw new Exception("Impossible de créer le conteneur Docker. Erreur : " + e.getMessage());
        }
    }
    
    // --- Le reste des méthodes (getStats, delete, etc.) ---
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DockerStatsData {
        @JsonProperty("CPUPerc") String cpuPercent;
        @JsonProperty("MemUsage") String memoryUsage;
        @JsonProperty("NetIO") String netIO;
        @JsonProperty("BlockIO") String blockIO;
    }

    public ServerStats getStats(String containerId) {
        ServerStats stats = new ServerStats();
        if (containerId == null || containerId.isBlank()) {
            stats.setError("Container ID is missing.");
            return stats;
        }
        try {
            ProcessBuilder checkPb = new ProcessBuilder("docker", "inspect", "--format='{{.State.Running}}'", containerId);
            Process checkP = checkPb.start();
            String isRunning = new BufferedReader(new InputStreamReader(checkP.getInputStream())).readLine();
            if (checkP.waitFor() != 0 || !"true".equals(isRunning.replace("'", ""))) {
                return stats;
            }

            ProcessBuilder pb = new ProcessBuilder("docker", "stats", "--no-stream", "--format", "{{json .}}", containerId);
            Process p = pb.start();
            String statsJson = new BufferedReader(new InputStreamReader(p.getInputStream())).readLine();
            p.waitFor();

            ObjectMapper mapper = new ObjectMapper();
            DockerStatsData data = mapper.readValue(statsJson, DockerStatsData.class);
            
            stats.setCpuPercent(data.cpuPercent);
            stats.setMemoryUsage(data.memoryUsage);
            stats.setNetIO(data.netIO);
            stats.setBlockIO(data.blockIO);
            stats.setOffline(false);

            return stats;
        } catch (Exception e) {
            log.warn("Impossible de récupérer les stats pour le conteneur {}: {}", containerId, e.getMessage());
            stats.setError(e.getMessage());
            return stats;
        }
    }

    public void deleteServerContainer(String containerId) throws Exception {
        if (containerId == null || containerId.isEmpty()) {
            log.info("Aucun ID de conteneur fourni, suppression ignorée.");
            return;
        }
        try {
            log.info("Tentative d'arrêt du conteneur : {}", containerId);
            try {
                ProcessBuilder stopProcessBuilder = new ProcessBuilder("docker", "stop", containerId);
                Process stopProcess = stopProcessBuilder.start();
                stopProcess.waitFor();
            } catch (Exception e) {
                log.warn("Impossible d'arrêter le conteneur (il est peut-être déjà arrêté) : {}", e.getMessage());
            }
            log.info("Tentative de suppression du conteneur : {}", containerId);
            ProcessBuilder removeProcessBuilder = new ProcessBuilder("docker", "rm", containerId);
            Process removeProcess = removeProcessBuilder.start();
            int exitCode = removeProcess.waitFor();
            if (exitCode != 0) {
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(removeProcess.getErrorStream()));
                throw new RuntimeException("Erreur Docker lors de la suppression : " + errorReader.readLine());
            }
            log.info("Conteneur {} supprimé avec succès !", containerId);
        } catch (Exception e) {
            log.error("Impossible de supprimer le conteneur Docker {}.", containerId, e);
            throw new Exception("Impossible de supprimer le conteneur Docker. Erreur : " + e.getMessage());
        }
    }

    public void performServerAction(String containerId, String action) throws Exception {
        if (containerId == null || containerId.isEmpty()) {
            throw new Exception("ID de conteneur invalide.");
        }
        ProcessBuilder pb = new ProcessBuilder("docker", action, containerId);
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            throw new RuntimeException("Erreur Docker lors de l'action '" + action + "': " + errorReader.readLine());
        }
        log.info("Action '{}' exécutée avec succès pour le conteneur {}", action, containerId);
    }
    
    public void pullLlmModel(String containerId, String modelName) throws Exception {
        if (containerId == null || containerId.isBlank() || modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("L'ID du conteneur et le nom du modèle sont requis.");
        }
        
        log.info("Tentative de téléchargement du modèle '{}' pour le conteneur {}", modelName, containerId);

        List<String> command = List.of("docker", "exec", containerId, "ollama", "pull", modelName);
        
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();

            new Thread(() -> new BufferedReader(new InputStreamReader(process.getInputStream())).lines().forEach(log::info)).start();
            new Thread(() -> new BufferedReader(new InputStreamReader(process.getErrorStream())).lines().forEach(log::error)).start();

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.error("La commande 'ollama pull' a échoué avec le code de sortie : {}", exitCode);
                throw new RuntimeException("Échec du téléchargement du modèle. Vérifiez les logs pour plus de détails.");
            }
            
            log.info("Le modèle '{}' a été téléchargé avec succès pour le conteneur {}.", modelName, containerId);

        } catch (Exception e) {
            log.error("Impossible d'exécuter la commande de téléchargement pour le modèle '{}'.", modelName, e);
            throw e;
        }
    }
}