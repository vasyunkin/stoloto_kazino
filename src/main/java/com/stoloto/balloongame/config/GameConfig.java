package com.stoloto.balloongame.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Runtime-configurable game parameters.
 * Loaded from application.yml (prefix "game").
 * At round start, a deep copy is stored in GameSession.configSnapshot (S4/I8).
 * During FLYING, all math reads configSnapshot — NOT this live bean.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "game")
public class GameConfig {

    @Positive
    private int greenLevels = 9;

    @Positive
    private int redLevels = 12;

    @Positive
    private int pointsPerLine = 10;

    @Positive
    private double ascentSpeedLinesPerSec = 1.5;

    @Valid
    private MathConfig math = new MathConfig();

    @Valid
    private BoostersConfig boosters = new BoostersConfig();

    @Valid
    private AdminConfig admin = new AdminConfig();

    @Valid
    private ScoringConfig scoring = new ScoringConfig();

    @Valid
    private ProvablyFairConfig provablyFair = new ProvablyFairConfig();

    // ── Nested configs ────────────────────────────────────────────────────

    @Data
    public static class MathConfig {

        /** Exponential growth rate r in K(t) = 1 + (e^(r*t) - 1). */
        @Positive
        private double growthRate = 0.065;

        /** House edge fraction [0, 1). E.g. 0.04 = 4%. */
        @DecimalMin("0.0") @DecimalMax("0.5")
        private double houseEdge = 0.04;

        /** Probability of instant crash at 1.00x [0, 1). */
        @DecimalMin("0.0") @DecimalMax("1.0")
        private double instantCrashRate = 0.03;
    }

    @Data
    public static class BoostersConfig {

        /** Probability that a booster spawns in a round [0, 1]. */
        @DecimalMin("0.0") @DecimalMax("1.0")
        private double spawnProbability = 0.70;

        @Valid
        @NotEmpty
        private List<BoosterTier> tiers = new ArrayList<>();
    }

    @Data
    public static class BoosterTier {

        @Positive
        private int tier;

        @NotBlank
        private String name;

        /** Win multiplier applied to score on activation. */
        @Positive
        private double multiplier;

        /** Relative weight for weighted random selection. */
        @Positive
        private int probabilityWeight;
    }

    @Data
    public static class AdminConfig {

        /** Secret key for X-Admin-Key header (override via env GAME_ADMIN_KEY). */
        @NotBlank
        private String apiKey = "change-me-in-prod";

        @NotNull @Positive
        private BigDecimal minBetAmount = new BigDecimal("10.0");

        @NotNull @Positive
        private BigDecimal maxBetAmount = new BigDecimal("10000.0");

        @NotNull @Positive
        private BigDecimal minWinAmount = new BigDecimal("50.0");

        @NotNull @Positive
        private BigDecimal maxWinMultiplier = new BigDecimal("100.0");

        /** Allow /api/players/{id}/deposit endpoint. Set false in prod. */
        private boolean allowDeposit = true;
    }

    @Data
    public static class ScoringConfig {

        /** RED-zone line points multiplier. Default 1.0 = same as GREEN (jury-tunable). */
        @DecimalMin("0.0")
        private double redZoneMultiplier = 1.0;
    }

    @Data
    public static class ProvablyFairConfig {

        @Positive
        private int serverSeedLengthBytes = 32;

        @NotBlank
        private String hashAlgorithm = "SHA-256";
    }

    // ── Deep copy (used in S4 to snapshot config at round start) ─────────

    /**
     * Returns a deep copy of this config for use as an immutable GameSession snapshot.
     * During FLYING, only this snapshot is used for all math — not the live bean.
     */
    public GameConfig deepCopy() {
        GameConfig copy = new GameConfig();
        copy.greenLevels = this.greenLevels;
        copy.redLevels = this.redLevels;
        copy.pointsPerLine = this.pointsPerLine;
        copy.ascentSpeedLinesPerSec = this.ascentSpeedLinesPerSec;

        ScoringConfig sc = new ScoringConfig();
        if (this.scoring != null) {
            sc.redZoneMultiplier = this.scoring.redZoneMultiplier;
        }
        copy.scoring = sc;

        MathConfig m = new MathConfig();
        m.growthRate = this.math.growthRate;
        m.houseEdge = this.math.houseEdge;
        m.instantCrashRate = this.math.instantCrashRate;
        copy.math = m;

        BoostersConfig b = new BoostersConfig();
        b.spawnProbability = this.boosters.spawnProbability;
        List<BoosterTier> tiers = new ArrayList<>();
        for (BoosterTier t : this.boosters.tiers) {
            BoosterTier tc = new BoosterTier();
            tc.tier = t.tier;
            tc.name = t.name;
            tc.multiplier = t.multiplier;
            tc.probabilityWeight = t.probabilityWeight;
            tiers.add(tc);
        }
        b.tiers = tiers;
        copy.boosters = b;

        AdminConfig a = new AdminConfig();
        a.apiKey = this.admin.apiKey;
        a.minBetAmount = this.admin.minBetAmount;
        a.maxBetAmount = this.admin.maxBetAmount;
        a.minWinAmount = this.admin.minWinAmount;
        a.maxWinMultiplier = this.admin.maxWinMultiplier;
        a.allowDeposit = this.admin.allowDeposit;
        copy.admin = a;

        ProvablyFairConfig pf = new ProvablyFairConfig();
        pf.serverSeedLengthBytes = this.provablyFair.serverSeedLengthBytes;
        pf.hashAlgorithm = this.provablyFair.hashAlgorithm;
        copy.provablyFair = pf;

        return copy;
    }
}
