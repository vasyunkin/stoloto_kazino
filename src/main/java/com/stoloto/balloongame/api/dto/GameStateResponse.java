package com.stoloto.balloongame.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.stoloto.balloongame.domain.entity.RoundStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code GET /api/game/state/{gameId}}.
 *
 * <p>I1: {@code crashPoint} and {@code serverSeed} are null (omitted from JSON)
 * while {@code status = FLYING}. {@code puzzlePieceIndex} only after terminal win/loss (S12).
 * {@code pointsDelta} is the increment on this poll for VFX (S15).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Пока FLYING: crashPoint, serverSeed и puzzlePieceIndex отсутствуют. Poll 100–250 ms")
public record GameStateResponse(
        UUID gameId,
        RoundStatus status,
        BigDecimal multiplier,
        int lineIndex,
        String zone,
        int pointsTotal,
        int pointsDelta,
        BoosterStateDto booster,
        BigDecimal crashPoint,
        String serverSeed,
        BigDecimal winAmount,
        Integer puzzlePieceIndex
) {}
