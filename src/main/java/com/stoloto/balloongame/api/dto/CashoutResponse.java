package com.stoloto.balloongame.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code POST /api/game/cashout/{gameId}} — terminal, so {@code serverSeed} is revealed.
 */
public record CashoutResponse(
        UUID gameId,
        BigDecimal multiplierAtCashout,
        BigDecimal winAmount,
        int pointsTotal,
        String serverSeed
) {}
