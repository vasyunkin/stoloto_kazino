package com.stoloto.balloongame;

import com.stoloto.balloongame.config.GameConfig;
import com.stoloto.balloongame.domain.entity.RoundStatus;
import com.stoloto.balloongame.service.GameSession;
import com.stoloto.balloongame.service.GameSessionCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S4 — in-memory session registry and per-gameId lock identity (prep for I4).
 */
class GameSessionCacheTest {

    private GameSessionCache cache;

    @BeforeEach
    void setUp() {
        cache = new GameSessionCache();
    }

    @Test
    void putGetEvict() {
        GameSession session = session(UUID.randomUUID());
        cache.put(session);

        assertThat(cache.get(session.getGameId())).containsSame(session);

        cache.evict(session.getGameId());
        assertThat(cache.get(session.getGameId())).isEmpty();
    }

    @Test
    void lockFor_sameGameId_returnsSameLock() {
        UUID id = UUID.randomUUID();
        ReentrantLock a = cache.lockFor(id);
        ReentrantLock b = cache.lockFor(id);
        assertThat(a).isSameAs(b);
        assertThat(cache.lockFor(UUID.randomUUID())).isNotSameAs(a);
    }

    @Test
    void nextNonce_isStrictlyIncreasing() {
        long n1 = cache.nextNonce();
        long n2 = cache.nextNonce();
        assertThat(n2).isGreaterThan(n1);
    }

    private static GameSession session(UUID id) {
        return GameSession.builder()
                .gameId(id)
                .playerId("p")
                .betAmount(new BigDecimal("10"))
                .balloonType("STANDARD")
                .crashPoint(new BigDecimal("1.5000"))
                .startTime(Instant.parse("2026-01-01T00:00:00Z"))
                .serverSeedHex("ab".repeat(32))
                .clientSeed("")
                .nonce(1L)
                .commitHash("00")
                .configSnapshot(new GameConfig())
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .status(RoundStatus.FLYING)
                .build();
    }
}
