package com.stoloto.balloongame.service;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-memory registry of {@link GameSession} keyed by {@code gameId}.
 *
 * <p>Lock registry is created in S4 so S5 can share one {@code ReentrantLock}
 * per round for state and cashout (I4). TTL eviction of terminal sessions is S7.
 */
@Component
public class GameSessionCache {

    private final ConcurrentHashMap<UUID, GameSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final AtomicLong nonceSequence = new AtomicLong(0);

    public void put(GameSession session) {
        sessions.put(session.getGameId(), session);
        lockFor(session.getGameId());
    }

    public Optional<GameSession> get(UUID gameId) {
        return Optional.ofNullable(sessions.get(gameId));
    }

    public ReentrantLock lockFor(UUID gameId) {
        return locks.computeIfAbsent(gameId, key -> new ReentrantLock());
    }

    public void evict(UUID gameId) {
        sessions.remove(gameId);
        locks.remove(gameId);
    }

    /**
     * Global monotonically increasing nonce for Provably Fair (S4).
     * Gaps after a rolled-back start are acceptable.
     */
    public long nextNonce() {
        return nonceSequence.incrementAndGet();
    }
}
