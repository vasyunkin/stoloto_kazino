package com.stoloto.balloongame.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body of {@code POST /api/game/start}.
 *
 * {@code betAmount} floor here is bean-validation only; the orchestrator
 * enforces {@code [minBet, maxBet]} from the round's config snapshot.
 */
public record BetRequest(

        @NotBlank(message = "playerId is required")
        String playerId,

        @NotNull(message = "betAmount is required")
        @DecimalMin(value = "0.01", message = "betAmount must be > 0")
        BigDecimal betAmount,

        @NotBlank(message = "balloonType is required")
        @Pattern(regexp = "STANDARD|LUCKY", message = "balloonType must be STANDARD or LUCKY")
        String balloonType,

        /** {@code NONE} skips booster roll; {@code AUTO} or null — server rolls by config. */
        String boosterPreference,

        @Size(max = 64, message = "clientSeed must be at most 64 characters")
        String clientSeed
) {}
