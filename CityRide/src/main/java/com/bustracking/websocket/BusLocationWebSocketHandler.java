package com.bustracking.websocket;

import com.bustracking.dto.BusResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BusLocationWebSocketHandler extends TextWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(BusLocationWebSocketHandler.class);

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;

    public BusLocationWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(new ConcurrentWebSocketSessionDecorator(session, 10_000, 512 * 1024));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        session.close(CloseStatus.NOT_ACCEPTABLE);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.removeIf(active -> active.getId().equals(session.getId()));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws IOException {
        sessions.removeIf(active -> active.getId().equals(session.getId()));
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
        logger.debug("Location WebSocket transport ended: {}", exception.getMessage());
    }

    public void broadcast(BusResponseDTO location) {
        final String payload;
        try {
            payload = objectMapper.writeValueAsString(location);
        } catch (JacksonException exception) {
            logger.error("Could not serialize bus location update", exception);
            return;
        }

        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(payload));
                } else {
                    sessions.remove(session);
                }
            } catch (IOException exception) {
                sessions.remove(session);
                logger.debug("Could not deliver a bus location update to a WebSocket client", exception);
                try {
                    session.close(CloseStatus.SERVER_ERROR);
                } catch (IOException closeException) {
                    logger.debug("Could not close the failed location WebSocket client", closeException);
                }
            }
        }
    }
}
