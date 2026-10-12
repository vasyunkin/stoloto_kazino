package com.stoloto.balloongame.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * Booster payload inside {@link GameStateResponse}. {@code null} on the parent
 * when no booster spawned. Scoring bonus itself is S6.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
/**
 * Before activation, only {@code spawned} is set. Line, tier and multiplier stay server-side
 * because the trigger line is always strictly before the crash.
 */
public record BoosterStateDto(
        boolean spawned,
        Integer tier,
        String name,
        BigDecimal multiplier,
        Integer triggerLine,
        boolean activated
) {}
