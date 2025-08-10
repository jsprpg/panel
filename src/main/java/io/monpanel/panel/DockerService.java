package io.monpanel.panel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DockerService {

    private static final Logger log = LoggerFactory.getLogger(DockerService.class);

    public String createServer(Server server, GameEgg egg) throws Exception {
        log.info("Préparation de l'environnement pour le serveur : {}", server.getName());

        String serverUuid = UUID.randomUUID().toString();
        Path hostPath = Paths.get("servers", serverUuid);
        Files.createDirectories(hostPath);
        server.setHostPath(hostPath.toAbsolutePath().toString());

        log.info("Lancement de la commande Docker pour l'image : {}", server.getDockerImage());

        List<String> command = new ArrayList<>(List.of(
            "docker", "run", "-d",
            "--memory=" + server.getMemory() + "m",
            "--cpus=" + String.valueOf(server.getCpu()),
            "--name", server.getName().replaceAll("\\s+", "_") + "_" + System.currentTimeMillis()
        ));

        if (egg.getPorts() != null) {
            for (Map.Entry<String, String> entry : egg.getPorts().entrySet()) {
                command.add("-p");
                command.add(entry.getKey() + ":" + entry.getValue());
            }
        }

        command.add("-v");
        command.add(server.getHostPath() + ":/data");

        if (egg.getEnvironment() != null) {
            for (Map.Entry<String, String> entry : egg.getEnvironment().entrySet()) {
                command.add("-e");
                command.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        
        command.add(server.getDockerImage());

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String containerId = reader.readLine();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String errorLine = errorReader.readLine();
                log.error("Erreur Docker (exit code {}): {}", exitCode, errorLine);
                throw new RuntimeException("Erreur Docker : " + errorLine);
            }
            if (containerId == null || containerId.isEmpty()) {
                throw new RuntimeException("N'a pas pu récupérer l'ID du conteneur. Docker est-il bien installé et en cours d'exécution ?");
            }
            log.info("Conteneur créé avec succès ! ID : {}", containerId);
            return containerId.substring(0, 12);
        } catch (Exception e) {
            log.error("Impossible de créer le conteneur Docker.", e);
            throw new Exception("Impossible de créer le conteneur Docker. Erreur : " + e.getMessage());
        }
    }
    
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