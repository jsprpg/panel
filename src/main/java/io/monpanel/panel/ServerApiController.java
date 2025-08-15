package io.monpanel.panel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.github.koraktor.steamcondenser.steam.servers.SourceServer;

@RestController
public class ServerApiController {

    private static final Logger log = LoggerFactory.getLogger(ServerApiController.class);

    @Autowired
    private ServerRepository serverRepository;

    @Autowired
    private DockerService dockerService;

    @Autowired
    private EggService eggService;

    private Path resolveServerPath(Server server, String relativePath) {
        Path serverHostPath = Paths.get(server.getHostPath());
        Path finalPath = serverHostPath.resolve(relativePath).normalize();
        if (!finalPath.startsWith(serverHostPath)) {
            throw new SecurityException("Accès non autorisé au chemin : " + finalPath);
        }
        return finalPath;
    }

    @GetMapping("/api/server/{id}/stats")
    public ServerStats getServerStats(@PathVariable Long id) {
        Server server = serverRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Serveur non trouvé"));
        return dockerService.getStats(server.getContainerId());
    }

    @PostMapping("/api/server/{id}/command")
    public String sendServerCommand(@PathVariable Long id, @RequestBody String command) {
        Server server = serverRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Serveur non trouvé"));
        
        int rconPort = server.getHostPort() + 1;
        String rconPassword = "supersecretpassword";
        String host = "localhost";

        SourceServer gameServer = null;
        try {
            gameServer = new SourceServer(host, rconPort);
            gameServer.rconAuth(rconPassword);
            String response = gameServer.rconExec(command);
            log.info("Réponse du serveur RCON : {}", response);
            return response;
        } catch (Exception e) {
            log.error("Erreur RCON (Steam Condenser): {}", e.getMessage());
            return "Erreur lors de l'envoi de la commande : " + e.getMessage();
        } finally {
            if (gameServer != null) {
                gameServer.disconnect();
            }
        }
    }
    
    @GetMapping("/api/server/{id}/files")
    public ResponseEntity<List<FileObject>> listFiles(@PathVariable Long id, @RequestParam(defaultValue = ".") String path) {
        Server server = serverRepository.findById(id).orElseThrow(() -> new RuntimeException("Serveur non trouvé"));
        try {
            Path targetPath = resolveServerPath(server, path);
            List<FileObject> files = Files.list(targetPath).map(p -> {
                FileObject file = new FileObject();
                file.setName(p.getFileName().toString());
                file.setDirectory(Files.isDirectory(p));
                try {
                    file.setSize(Files.size(p));
                    FileTime lastModified = Files.getLastModifiedTime(p);
                    file.setModifiedDate(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(lastModified.toMillis())));
                } catch (IOException e) {
                    log.warn("Impossible de lire les attributs du fichier {}", p.toString());
                }
                return file;
            }).collect(Collectors.toList());
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            log.error("Erreur de listage de fichiers pour {}: {}", server.getName(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/api/server/{id}/files/content")
    public ResponseEntity<String> getFileContent(@PathVariable Long id, @RequestParam String path) {
        Server server = serverRepository.findById(id).orElseThrow(() -> new RuntimeException("Serveur non trouvé"));
        try {
            Path targetFile = resolveServerPath(server, path);
            String content = Files.readString(targetFile, StandardCharsets.UTF_8);
            return ResponseEntity.ok(content);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/api/server/{id}/files/content")
    public ResponseEntity<String> saveFileContent(@PathVariable Long id, @RequestParam String path, @RequestBody String content) {
        Server server = serverRepository.findById(id).orElseThrow(() -> new RuntimeException("Serveur non trouvé"));
        try {
            Path targetFile = resolveServerPath(server, path);
            Files.writeString(targetFile, content, StandardCharsets.UTF_8);
            return ResponseEntity.ok("Fichier sauvegardé.");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
    
    @PostMapping("/api/server/{id}/files/upload")
    public ResponseEntity<String> uploadFile(@PathVariable Long id, @RequestParam String path, @RequestParam MultipartFile file) {
        Server server = serverRepository.findById(id).orElseThrow(() -> new RuntimeException("Serveur non trouvé"));
        try {
            Path destinationDir = resolveServerPath(server, path);
            Path destinationFile = destinationDir.resolve(file.getOriginalFilename());
            Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);
            return ResponseEntity.ok("Fichier uploadé.");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/api/server/{id}/files/create-directory")
    public ResponseEntity<String> createDirectory(@PathVariable Long id, @RequestParam String path, @RequestParam String dirName) {
        Server server = serverRepository.findById(id).orElseThrow(() -> new RuntimeException("Serveur non trouvé"));
        try {
            Path newDir = resolveServerPath(server, path).resolve(dirName);
            Files.createDirectories(newDir);
            return ResponseEntity.ok("Dossier créé.");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/api/server/{id}/files/create-file")
    public ResponseEntity<String> createFile(@PathVariable Long id, @RequestParam String path, @RequestParam String fileName) {
        Server server = serverRepository.findById(id).orElseThrow(() -> new RuntimeException("Serveur non trouvé"));
        try {
            Path newFile = resolveServerPath(server, path).resolve(fileName);
            Files.createFile(newFile);
            return ResponseEntity.ok("Fichier créé.");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/api/server/{id}/files/delete")
    public ResponseEntity<String> deleteFile(@PathVariable Long id, @RequestParam String path) {
        Server server = serverRepository.findById(id).orElseThrow(() -> new RuntimeException("Serveur non trouvé"));
        try {
            Path target = resolveServerPath(server, path);
            if (Files.isDirectory(target)) {
                FileSystemUtils.deleteRecursively(target);
            } else {
                Files.delete(target);
            }
            return ResponseEntity.ok("Élément supprimé.");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/api/server/{id}/files/rename")
    public ResponseEntity<String> renameFile(@PathVariable Long id, @RequestParam String oldPath, @RequestParam String newPath) {
        Server server = serverRepository.findById(id).orElseThrow(() -> new RuntimeException("Serveur non trouvé"));
        try {
            Path oldTarget = resolveServerPath(server, oldPath);
            Path newTarget = resolveServerPath(server, newPath);
            Files.move(oldTarget, newTarget);
            return ResponseEntity.ok("Élément renommé.");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
    
    @GetMapping("/api/server/{id}/files/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id, @RequestParam String path) {
        Server server = serverRepository.findById(id).orElseThrow(() -> new RuntimeException("Serveur non trouvé"));
        try {
            Path targetFile = resolveServerPath(server, path);
            Resource resource = new FileSystemResource(targetFile);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/api/server/{id}/llm/pull")
    public ResponseEntity<String> pullLlmModel(@PathVariable Long id, @RequestBody String modelName) {
        Server server = serverRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Serveur non trouvé"));
        
        String cleanModelName = modelName.replaceAll("\"", "").trim();

        try {
            dockerService.pullLlmModel(server.getContainerId(), cleanModelName);
            return ResponseEntity.ok("Téléchargement du modèle '" + cleanModelName + "' lancé avec succès.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Échec du téléchargement du modèle : " + e.getMessage());
        }
    }

    @PostMapping("/api/server/{id}/change-port")
    public ResponseEntity<String> changeServerPort(@PathVariable Long id, @RequestParam int newPort) {
        Server server = serverRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Serveur non trouvé"));

        Optional<GameEgg> foundEggOpt = eggService.getGroupedEggs().values().stream()
            .flatMap(sub -> sub.values().stream())
            .flatMap(java.util.List::stream)
            .filter(egg -> server.getDockerImage().equals(egg.getDocker_image()))
            .findFirst();

        if (foundEggOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Configuration (Egg) introuvable.");
        }
        GameEgg egg = foundEggOpt.get();

        try {
            dockerService.deleteServerContainer(server.getContainerId());
            server.setHostPort(newPort);
            String newContainerId;
            if ("server".equals(egg.getView_type()) && egg.getDocker_image().contains("itzg/minecraft-server")) {
                newContainerId = dockerService.createMinecraftServer(server, egg);
            } else {
                newContainerId = dockerService.createGenericServer(server, egg);
            }
            server.setContainerId(newContainerId);
            serverRepository.save(server);
            return ResponseEntity.ok("Le port a été changé et le serveur a été redémarré avec succès.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur lors du changement de port : " + e.getMessage());
        }
    }

    @PostMapping("/api/server/{id}/a1111/install-model")
    public ResponseEntity<String> installA1111Model(@PathVariable Long id, @RequestBody String modelUrl) {
        Server server = serverRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Serveur non trouvé"));
        
        String cleanModelUrl = modelUrl.replaceAll("\"", "").trim();

        try {
            dockerService.installA1111Model(server.getContainerId(), cleanModelUrl);
            return ResponseEntity.ok("Installation du modèle lancée.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Échec de l'installation : " + e.getMessage());
        }
    }
}