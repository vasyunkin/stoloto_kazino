package com.stoloto.balloongame.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * Booster payload inside {@link GameStateResponse}. {@code null} on the parent
 * when no booster spawned. Scoring bonus itself is S6.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BoosterStateDto(
        boolean spawned,
        int tier,
        String name,
        BigDecimal multiplier,
        int triggerLine,
        boolean activated
) {}
