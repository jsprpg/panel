package io.monpanel.panel;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class ConsoleSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ConsoleSocketHandler.class);
    private final ServerRepository serverRepository;
    private final Map<String, DockerProcess> runningProcesses = new ConcurrentHashMap<>();

    private static class DockerProcess {
        final Process process;
        final OutputStream stdin;
        DockerProcess(Process process) {
            this.process = process;
            this.stdin = process.getOutputStream();
        }
    }

    public ConsoleSocketHandler(ServerRepository serverRepository) {
        this.serverRepository = serverRepository;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String serverIdStr = session.getUri().getPath().split("/")[2];
        Long serverId = Long.parseLong(serverIdStr);
        Server server = serverRepository.findById(serverId).orElse(null);

        if (server == null || server.getContainerId() == null) {
            session.sendMessage(new TextMessage("Erreur: Serveur ou conteneur introuvable."));
            session.close();
            return;
        }

        ProcessBuilder processBuilder = new ProcessBuilder("docker", "attach", server.getContainerId());
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        runningProcesses.put(session.getId(), new DockerProcess(process));

        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null && session.isOpen()) {
                    session.sendMessage(new TextMessage(line));
                }
            } catch (Exception e) {
                log.warn("Erreur de lecture du log pour la session {} : {}", session.getId(), e.getMessage());
            }
        }).start();
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String command = message.getPayload();
        DockerProcess dockerProcess = runningProcesses.get(session.getId());

        if (dockerProcess != null && dockerProcess.process.isAlive()) {
            try {
                dockerProcess.stdin.write((command + "\n").getBytes());
                dockerProcess.stdin.flush();
            } catch (Exception e) {
                log.error("Erreur d'écriture de la commande : {}", e.getMessage());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("Session WebSocket fermée : {}. Arrêt du processus d'attachement.", session.getId());
        DockerProcess dockerProcess = runningProcesses.remove(session.getId());
        if (dockerProcess != null && dockerProcess.process.isAlive()) {
            dockerProcess.process.destroyForcibly();
        }
    }
}