package com.stoloto.balloongame.api.dto;

import java.math.BigDecimal;

/**
 * Public per-theme bet bounds. No secrets, no house edge.
 */
public record BetLimitsResponse(ThemeLimit standard, ThemeLimit lucky) {

    public record ThemeLimit(BigDecimal minBetAmount, BigDecimal maxBetAmount) {}
}
