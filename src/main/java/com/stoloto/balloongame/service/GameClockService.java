package com.stoloto.balloongame.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;

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

    public BigDecimal currentMultiplier(GameSession session) {
        return crashMath.multiplierAt(
                session.getStartTime(),
                clock.instant(),
                session.getConfigSnapshot().getMath().getGrowthRate());
    }

    public int currentLine(GameSession session) {
        return crashMath.lineIndexAt(
                session.getStartTime(),
                clock.instant(),
                session.getConfigSnapshot().getAscentSpeedLinesPerSec());
    }
}
