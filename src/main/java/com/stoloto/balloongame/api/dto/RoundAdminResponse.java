package com.stoloto.balloongame.api.dto;

import com.stoloto.balloongame.domain.entity.RoundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Operator view of one round, including the seed. Not a player endpoint. */
public record RoundAdminResponse(
        UUID gameId,
        String playerId,
        RoundStatus status,
        BigDecimal betAmount,
        BigDecimal winAmount,
        BigDecimal crashPoint,
        BigDecimal cashoutMultiplier,
        String serverSeed,
        String commitHash,
        String seedPrecommit,
        String clientSeed,
        long nonce,
        String algorithmVersion,
        Instant startedAt,
        Instant endedAt
) {}
