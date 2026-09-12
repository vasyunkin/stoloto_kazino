package com.stoloto.balloongame.ws;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Active STOMP watches: {@code gameId → playerId} (owner from CONNECT header).
 */
@Component
public class GameWsSubscriptionRegistry {

    private final ConcurrentHashMap<UUID, String> watches = new ConcurrentHashMap<>();

    public void register(UUID gameId, String playerId) {
        watches.put(gameId, playerId);
    }

    public void unregister(UUID gameId) {
        watches.remove(gameId);
    }

    public void unregisterSessionGames(Collection<UUID> gameIds) {
        gameIds.forEach(watches::remove);
    }

    public Map<UUID, String> snapshot() {
        return Map.copyOf(watches);
    }

    public boolean isWatched(UUID gameId) {
        return watches.containsKey(gameId);
    }
}
