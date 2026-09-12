package com.stoloto.balloongame.ws;

import com.stoloto.balloongame.api.dto.PublicGameState;
import com.stoloto.balloongame.api.dto.WsGameEvent;
import com.stoloto.balloongame.api.exception.GameException;
import com.stoloto.balloongame.domain.entity.RoundStatus;
import com.stoloto.balloongame.service.GameOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Pushes public ticks to {@code /topic/game/{gameId}} using the same lifecycle resolve as REST (I3).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameWsTicker {

    private final GameWsSubscriptionRegistry registry;
    private final GameOrchestrator orchestrator;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedRateString = "${app.ws.tick-interval-ms:200}")
    public void pushWatchedRounds() {
        Map<UUID, String> snapshot = registry.snapshot();
        for (Map.Entry<UUID, String> entry : snapshot.entrySet()) {
            UUID gameId = entry.getKey();
            String playerId = entry.getValue();
            try {
                PublicGameState state = orchestrator.resolvePublicState(gameId, playerId);
                messagingTemplate.convertAndSend(
                        StompPlayerInterceptor.TOPIC_PREFIX + gameId,
                        new WsGameEvent(eventType(state.status()), state));
            } catch (GameException ex) {
                log.debug("WS tick dropped for gameId={}: {}", gameId, ex.getErrorCode());
                registry.unregister(gameId);
            } catch (RuntimeException ex) {
                log.warn("WS tick failed for gameId={}: {}", gameId, ex.toString());
            }
        }
    }

    static String eventType(RoundStatus status) {
        return switch (status) {
            case FLYING -> "tick";
            case CRASHED -> "crash";
            case CASHED_OUT -> "cashout";
            case VOID -> "void";
        };
    }
}
