package io.monpanel.panel;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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

    // --- CORRECTION ICI ---
    // On dit à notre parser JSON d'ignorer les champs inconnus comme "Container"
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DockerStatsData {
        @JsonProperty("CPUPerc") String cpuPercent;
        @JsonProperty("MemUsage") String memoryUsage;
        @JsonProperty("NetIO") String netIO;
        @JsonProperty("BlockIO") String blockIO;
    }

    public String createMinecraftServer(Server server) throws Exception {
        log.info("Préparation de l'environnement pour le serveur : {}", server.getName());

        String serverUuid = UUID.randomUUID().toString();
        Path hostPath = Paths.get("servers", serverUuid);
        Files.createDirectories(hostPath);
        server.setHostPath(hostPath.toAbsolutePath().toString());

        log.info("Lancement de la commande Docker pour le serveur : {}", server.getName());
        int rconPort = server.getHostPort() + 1;

        List<String> command = new ArrayList<>(List.of(
            "docker", "run", "-d",
            "-p", server.getHostPort() + ":25565",
            "-p", rconPort + ":25575",
            "-v", server.getHostPath() + ":/data",
            "-e", "EULA=TRUE",
            "-e", "ENABLE_RCON=true",
            "-e", "RCON_PASSWORD=supersecretpassword",
            "--memory=" + server.getMemory() + "m",
            "--cpus=" + String.valueOf(server.getCpu()),
            "--name", server.getName().replaceAll("\\s+", "_") + "_" + System.currentTimeMillis(),
            server.getDockerImage()
        ));

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String containerId = reader.readLine();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                throw new RuntimeException("Erreur Docker : " + errorReader.readLine());
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
}