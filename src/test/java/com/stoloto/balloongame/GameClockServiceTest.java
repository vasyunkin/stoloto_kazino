package com.stoloto.balloongame;

import com.stoloto.balloongame.config.GameConfig;
import com.stoloto.balloongame.domain.entity.RoundStatus;
import com.stoloto.balloongame.provablyfair.ProvablyFairService;
import com.stoloto.balloongame.service.CrashMathService;
import com.stoloto.balloongame.service.GameClockService;
import com.stoloto.balloongame.service.GameSession;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S4 — GameClockService is the single multiplier function (I3)
 * and reads only session.configSnapshot (I8).
 */
class GameClockServiceTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant NOW   = Instant.parse("2026-01-01T00:00:10Z"); // t = 10s

    @Test
    void currentMultiplier_usesSnapshotGrowthRate_notADifferentLiveValue() {
        GameConfig snapshot = new GameConfig();
        snapshot.getMath().setGrowthRate(0.065);

        GameSession session = sessionWith(snapshot);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        GameClockService svc = new GameClockService(clock, new CrashMathService(new ProvablyFairService()));

        BigDecimal fromClock = svc.currentMultiplier(session);

        CrashMathService math = new CrashMathService(new ProvablyFairService());
        BigDecimal expected = math.multiplierAt(START, NOW, 0.065);
        BigDecimal ifLiveWereUsed = math.multiplierAt(START, NOW, 0.5);

        assertThat(fromClock).isEqualByComparingTo(expected);
        assertThat(fromClock).isNotEqualByComparingTo(ifLiveWereUsed);
    }

    @Test
    void currentLine_usesSnapshotAscentSpeed() {
        GameConfig snapshot = new GameConfig();
        snapshot.setAscentSpeedLinesPerSec(1.5);

        GameSession session = sessionWith(snapshot);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        GameClockService svc = new GameClockService(clock, new CrashMathService(new ProvablyFairService()));

        assertThat(svc.currentLine(session)).isEqualTo(15); // floor(10 * 1.5)
    }

    private static GameSession sessionWith(GameConfig snapshot) {
        return GameSession.builder()
                .gameId(UUID.randomUUID())
                .playerId("p")
                .betAmount(new BigDecimal("100"))
                .balloonType("STANDARD")
                .crashPoint(new BigDecimal("2.0000"))
                .startTime(START)
                .serverSeedHex("ab".repeat(32))
                .clientSeed("")
                .nonce(1L)
                .commitHash("00")
                .configSnapshot(snapshot)
                .createdAt(START)
                .status(RoundStatus.FLYING)
                .build();
    }
}
