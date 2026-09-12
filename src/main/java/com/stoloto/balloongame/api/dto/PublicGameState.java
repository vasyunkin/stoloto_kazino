package com.stoloto.balloongame.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.stoloto.balloongame.domain.entity.RoundStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Public round projection for WebSocket (S14) and the non-secret part of REST state.
 *
 * <p><strong>I1:</strong> this type must never declare {@code crashPoint} or {@code serverSeed}
 * fields — absence is enforced by architecture tests, not {@code @JsonIgnore}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicGameState(
        UUID gameId,
        RoundStatus status,
        BigDecimal multiplier,
        int lineIndex,
        String zone,
        int pointsTotal,
        BoosterStateDto booster,
        BigDecimal winAmount,
        Integer puzzlePieceIndex
) {}
