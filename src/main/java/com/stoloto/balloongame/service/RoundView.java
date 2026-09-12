package com.stoloto.balloongame.service;

import com.stoloto.balloongame.domain.entity.RoundStatus;

import java.math.BigDecimal;

/**
 * Immutable view of a round after one {@code resolve} tick. Secrets stay on
 * {@link GameSession}; the orchestrator copies them into HTTP DTOs only when terminal (I1).
 *
 * @param pointsDelta points awarded on this tick (0 on repeat poll / terminal re-read) — S15 VFX
 */
public record RoundView(
        GameSession session,
        RoundStatus status,
        BigDecimal multiplier,
        int lineIndex,
        String zone,
        int pointsTotal,
        int pointsDelta
) {}
