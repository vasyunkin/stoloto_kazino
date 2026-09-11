package com.stoloto.balloongame.service;

import com.stoloto.balloongame.config.GameConfig;
import com.stoloto.balloongame.domain.entity.RoundStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * In-memory model of an active (or recently finished) round.
 *
 * <p>Not a JPA entity and not an API DTO. Source of truth for seed, crash point,
 * status and money remains PostgreSQL. Secrets here must never be copied into
 * a FLYING HTTP response (I1).
 *
 * <p>{@code configSnapshot} is a deep copy taken at start and is the only config
 * {@link GameClockService} may read while the round is alive (I8).
 */
@Getter
@Builder
public class GameSession {

    private final UUID gameId;
    private final String playerId;
    private final BigDecimal betAmount;
    private final String balloonType;
    private final BigDecimal crashPoint;
    private final Instant startTime;
    private final String serverSeedHex;
    private final String clientSeed;
    private final long nonce;
    private final String commitHash;
    private final Integer boostTier;
    private final Integer boostTriggerLine;
    private final BigDecimal boostMultiplier;
    private final GameConfig configSnapshot;
    private final Instant createdAt;

    @Setter
    @Builder.Default
    private volatile RoundStatus status = RoundStatus.FLYING;

    @Setter
    @Builder.Default
    private volatile boolean boostActivated = false;

    @Setter
    @Builder.Default
    private volatile int linesPassedSnapshot = 0;

    @Setter
    private volatile BigDecimal cashoutMultiplier;

    @Setter
    private volatile BigDecimal winAmount;
}
