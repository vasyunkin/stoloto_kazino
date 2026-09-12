package com.stoloto.balloongame;

import com.stoloto.balloongame.config.GameConfig;
import com.stoloto.balloongame.domain.entity.RoundStatus;
import com.stoloto.balloongame.service.GameSession;
import com.stoloto.balloongame.service.RoundLifecycleService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EffectiveMultiplierTest {

    @Test
    void effectiveMultiplier_withoutBoost_isRawK() {
        GameSession session = baseSession(null, false);
        assertThat(RoundLifecycleService.effectiveMultiplier(session, new BigDecimal("1.5000")))
                .isEqualByComparingTo("1.5000");
    }

    @Test
    void effectiveMultiplier_withActivatedBoost_multiplies() {
        GameSession session = baseSession(new BigDecimal("2.0"), true);
        assertThat(RoundLifecycleService.effectiveMultiplier(session, new BigDecimal("1.5000")))
                .isEqualByComparingTo("3.0000");
    }

    @Test
    void effectiveMultiplier_spawnedButNotActivated_isRawK() {
        GameSession session = baseSession(new BigDecimal("3.0"), false);
        assertThat(RoundLifecycleService.effectiveMultiplier(session, new BigDecimal("1.2000")))
                .isEqualByComparingTo("1.2000");
    }

    private static GameSession baseSession(BigDecimal boostMult, boolean activated) {
        GameSession s = GameSession.builder()
                .gameId(UUID.randomUUID())
                .playerId("p")
                .betAmount(new BigDecimal("100"))
                .balloonType("STANDARD")
                .crashPoint(new BigDecimal("5.0000"))
                .startTime(Instant.parse("2026-01-01T00:00:00Z"))
                .serverSeedHex("ab".repeat(32))
                .clientSeed("")
                .nonce(1L)
                .commitHash("c".repeat(64))
                .boostTier(boostMult == null ? null : 1)
                .boostTriggerLine(boostMult == null ? null : 2)
                .boostMultiplier(boostMult)
                .configSnapshot(new GameConfig())
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .status(RoundStatus.FLYING)
                .build();
        s.setBoostActivated(activated);
        return s;
    }
}
