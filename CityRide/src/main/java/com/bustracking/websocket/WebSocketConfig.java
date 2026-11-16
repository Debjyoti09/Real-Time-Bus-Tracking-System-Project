package com.bustracking.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final BusLocationWebSocketHandler locationHandler;
    private final String[] allowedOriginPatterns;

    public WebSocketConfig(
            BusLocationWebSocketHandler locationHandler,
            @Value("${app.websocket.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*,https://*.ngrok-free.dev,https://*.ngrok-free.app,https://*.ngrok.io}") String allowedOrigins) {
        this.locationHandler = locationHandler;
        this.allowedOriginPatterns = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(locationHandler, "/ws/location")
                .setAllowedOriginPatterns(allowedOriginPatterns);
    }
}
