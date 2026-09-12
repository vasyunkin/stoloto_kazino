package com.stoloto.balloongame.service;

import java.math.BigDecimal;

/**
 * Immutable result of a booster roll for one game round (I2 — fixed at start).
 *
 * @param tier         booster tier number (1 = weakest)
 * @param name         display name from config (e.g. "Nitro Thruster")
 * @param multiplier   score multiplier applied on activation (BigDecimal, TD-06)
 * @param triggerLine  0-based line index at which the booster activates;
 *                     always strictly less than the crash line
 */
public record BoosterResult(
        int        tier,
        String     name,
        BigDecimal multiplier,
        int        triggerLine
) {}
