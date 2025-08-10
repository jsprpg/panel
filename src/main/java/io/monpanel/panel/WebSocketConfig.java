package io.monpanel.panel;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ConsoleSocketHandler consoleSocketHandler;

    public WebSocketConfig(ConsoleSocketHandler consoleSocketHandler) {
        this.consoleSocketHandler = consoleSocketHandler;
    }

    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(consoleSocketHandler, "/console-socket/{serverId}");
    }
}