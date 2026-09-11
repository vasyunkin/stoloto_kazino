package com.stoloto.balloongame.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Ставка. playerId должен совпадать с заголовком X-Player-Id")
public record BetRequest(

        @NotBlank(message = "playerId is required")
        @Schema(example = "demo")
        String playerId,

        @NotNull(message = "betAmount is required")
        @DecimalMin(value = "0.01", message = "betAmount must be > 0")
        @Schema(example = "50.00")
        BigDecimal betAmount,

        @NotBlank(message = "balloonType is required")
        @Pattern(regexp = "STANDARD|LUCKY", message = "balloonType must be STANDARD or LUCKY")
        @Schema(example = "STANDARD", allowableValues = {"STANDARD", "LUCKY"})
        String balloonType,

        /** {@code NONE} skips booster roll; {@code AUTO} or null — server rolls by config. */
        @Schema(example = "AUTO", allowableValues = {"AUTO", "NONE"})
        String boosterPreference,

        @Size(max = 64, message = "clientSeed must be at most 64 characters")
        @Schema(description = "Опциональный client seed для Provably Fair")
        String clientSeed
) {}
