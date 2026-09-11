package com.stoloto.balloongame.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

/**
 * Single source of elapsed-time derived values for a round (I3).
 *
 * <p>Reads growth rate and ascent speed only from {@code session.configSnapshot}
 * (I8) — never from the live {@code GameConfig} bean. The injected {@link Clock}
 * is the only time source.
 *
 * <p>HTTP state/cashout endpoints are S5; this service exists from S4 so the
 * contract is fixed before those paths are wired.
 */
@Service
@RequiredArgsConstructor
public class GameClockService {

    private final Clock clock;
    private final CrashMathService crashMath;

    /** I3: single multiplier function. Pass one {@code now} per resolve tick. */
    public BigDecimal multiplier(GameSession session, Instant now) {
        return crashMath.multiplierAt(
                session.getStartTime(),
                now,
                session.getConfigSnapshot().getMath().getGrowthRate());
    }

    public int lineIndex(GameSession session, Instant now) {
        return crashMath.lineIndexAt(
                session.getStartTime(),
                now,
                session.getConfigSnapshot().getAscentSpeedLinesPerSec());
    }

    public BigDecimal currentMultiplier(GameSession session) {
        return multiplier(session, clock.instant());
    }

    public int currentLine(GameSession session) {
        return lineIndex(session, clock.instant());
    }
}
