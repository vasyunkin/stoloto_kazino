package com.stoloto.balloongame.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code GET /api/game/verify/{gameId}} — Provably Fair reveal after terminal status.
 */
public record VerifyResponse(
        UUID gameId,
        String serverSeed,
        String clientSeed,
        long nonce,
        String commitHash,
        BigDecimal crashPoint,
        String algorithmVersion,
        Integer boostTier,
        Integer boostTriggerLine,
        BigDecimal boostMultiplier
) {}
