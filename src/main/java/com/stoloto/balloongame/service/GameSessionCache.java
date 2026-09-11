package com.stoloto.balloongame.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-memory registry of {@link GameSession} keyed by {@code gameId}.
 *
 * <p>Terminal sessions expire after {@link #TERMINAL_TTL}; FLYING sessions are not TTL-evicted.
 * A FLYING cache miss is not rebuilt (S7) — the round is treated as expired.
 */
@Component
public class GameSessionCache {

    public static final Duration TERMINAL_TTL = Duration.ofMinutes(15);

    private final ConcurrentHashMap<UUID, GameSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final AtomicLong nonceSequence = new AtomicLong(0);
    private final Clock clock;

    public GameSessionCache(Clock clock) {
        this.clock = clock;
    }

    public void put(GameSession session) {
        sessions.put(session.getGameId(), session);
        lockFor(session.getGameId());
    }

    public Optional<GameSession> get(UUID gameId) {
        GameSession session = sessions.get(gameId);
        if (session == null) {
            return Optional.empty();
        }
        if (session.getStatus().isTerminal() && isTerminalExpired(session)) {
            evict(gameId);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public ReentrantLock lockFor(UUID gameId) {
        return locks.computeIfAbsent(gameId, key -> new ReentrantLock());
    }

    public void evict(UUID gameId) {
        sessions.remove(gameId);
        locks.remove(gameId);
    }

    public long nextNonce() {
        return nonceSequence.incrementAndGet();
    }

    private boolean isTerminalExpired(GameSession session) {
        Instant anchor = session.getEndedAt() != null ? session.getEndedAt() : session.getCreatedAt();
        return clock.instant().isAfter(anchor.plus(TERMINAL_TTL));
    }
}
