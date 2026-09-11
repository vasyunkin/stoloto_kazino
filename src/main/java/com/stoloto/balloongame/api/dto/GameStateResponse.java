package com.stoloto.balloongame.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.stoloto.balloongame.domain.entity.RoundStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code GET /api/game/state/{gameId}}.
 *
 * <p>I1: {@code crashPoint} and {@code serverSeed} are null (omitted from JSON)
 * while {@code status = FLYING}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameStateResponse(
        UUID gameId,
        RoundStatus status,
        BigDecimal multiplier,
        int lineIndex,
        String zone,
        int pointsTotal,
        BoosterStateDto booster,
        BigDecimal crashPoint,
        String serverSeed,
        BigDecimal winAmount
) {}
