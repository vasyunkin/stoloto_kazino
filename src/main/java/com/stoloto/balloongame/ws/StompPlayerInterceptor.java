package com.stoloto.balloongame.ws;

import com.stoloto.balloongame.api.exception.GameException;
import com.stoloto.balloongame.service.GameOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * STOMP auth: {@code X-Player-Id} on CONNECT; ownership check on SUBSCRIBE to {@code /topic/game/{id}} (I6).
 */
@Component
@RequiredArgsConstructor
public class StompPlayerInterceptor implements ChannelInterceptor {

    public static final String PLAYER_ID_ATTR = "playerId";
    public static final String SESSION_GAMES_ATTR = "watchedGameIds";

    public static final String TOPIC_PREFIX = "/topic/game/";

    private final GameOrchestrator orchestrator;
    private final GameWsSubscriptionRegistry registry;

    @Override
    public Message<?> preSend(@Nullable Message<?> message, MessageChannel channel) {
        if (message == null) {
            return null;
        }
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            String playerId = firstHeader(accessor, "X-Player-Id");
            if (playerId == null || playerId.isBlank()) {
                throw new MessageDeliveryException("Missing X-Player-Id");
            }
            Map<String, Object> attrs = accessor.getSessionAttributes();
            if (attrs == null) {
                attrs = new ConcurrentHashMap<>();
                accessor.setSessionAttributes(attrs);
            }
            attrs.put(PLAYER_ID_ATTR, playerId.trim());
            attrs.put(SESSION_GAMES_ATTR, ConcurrentHashMap.newKeySet());
            return message;
        }

        if (StompCommand.SUBSCRIBE.equals(command)) {
            String destination = accessor.getDestination();
            UUID gameId = parseGameId(destination);
            if (gameId == null) {
                return message;
            }
            String playerId = playerId(accessor);
            try {
                orchestrator.assertOwnedSession(gameId, playerId);
            } catch (GameException ex) {
                throw new MessageDeliveryException(message, ex);
            }
            registry.register(gameId, playerId);
            @SuppressWarnings("unchecked")
            var watched = (java.util.Set<UUID>) accessor.getSessionAttributes().get(SESSION_GAMES_ATTR);
            if (watched != null) {
                watched.add(gameId);
            }
            return message;
        }

        if (StompCommand.UNSUBSCRIBE.equals(command)) {
            UUID gameId = parseGameId(accessor.getDestination());
            if (gameId != null) {
                registry.unregister(gameId);
            }
            return message;
        }

        if (StompCommand.DISCONNECT.equals(command)) {
            Map<String, Object> attrs = accessor.getSessionAttributes();
            if (attrs != null) {
                @SuppressWarnings("unchecked")
                var watched = (java.util.Set<UUID>) attrs.get(SESSION_GAMES_ATTR);
                if (watched != null) {
                    registry.unregisterSessionGames(watched);
                    watched.clear();
                }
            }
        }
        return message;
    }

    private static String playerId(StompHeaderAccessor accessor) {
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs == null) {
            throw new MessageDeliveryException("Missing session");
        }
        Object id = attrs.get(PLAYER_ID_ATTR);
        if (!(id instanceof String s) || s.isBlank()) {
            throw new MessageDeliveryException("Missing X-Player-Id");
        }
        return s;
    }

    @Nullable
    static UUID parseGameId(@Nullable String destination) {
        if (destination == null || !destination.startsWith(TOPIC_PREFIX)) {
            return null;
        }
        String id = destination.substring(TOPIC_PREFIX.length());
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    private static String firstHeader(StompHeaderAccessor accessor, String name) {
        String v = accessor.getFirstNativeHeader(name);
        if (v != null) {
            return v;
        }
        return accessor.getFirstNativeHeader(name.toLowerCase());
    }
}
