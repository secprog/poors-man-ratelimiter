package com.example.admin.websocket;

import com.example.admin.dto.WebSocketMessage;
import com.example.admin.service.AnalyticsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsWebSocketHandler implements WebSocketHandler {

    private final AnalyticsBroadcaster broadcaster;
    private final AnalyticsService analyticsService;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        log.info("Analytics WebSocket connection opened: {}", session.getId());

        return session.send(
                Flux.merge(
                        // Initial snapshot
                        analyticsService.getLatestUpdate()
                                .flatMapMany(update -> Flux.just(new WebSocketMessage("snapshot", update))
                                        .map(msg -> toWebSocketMessage(msg, session))),
                        // Stream updates
                        broadcaster.getUpdates()
                                .map(update -> new WebSocketMessage("summary", update))
                                .map(msg -> toWebSocketMessage(msg, session))
                )
        ).doFinally(signalType -> {
            log.info("Analytics WebSocket connection closed: {} - {}", session.getId(), signalType);
        });
    }

    private org.springframework.web.reactive.socket.WebSocketMessage toWebSocketMessage(WebSocketMessage wsMsg, WebSocketSession session) {
        try {
            String json = objectMapper.writeValueAsString(wsMsg);
            byte[] bytes = json.getBytes();
            DataBufferFactory bufferFactory = session.bufferFactory();
            return new org.springframework.web.reactive.socket.WebSocketMessage(
                    org.springframework.web.reactive.socket.WebSocketMessage.Type.TEXT, 
                    bufferFactory.wrap(bytes)
            );
        } catch (Exception e) {
            log.error("Error converting to WebSocket message", e);
            throw new RuntimeException(e);
        }
    }
}
