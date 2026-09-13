package com.stoloto.balloongame.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonMerge;
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

    @JsonMerge
    @Valid
    private MathConfig math = new MathConfig();

    @JsonMerge
    @Valid
    private BoostersConfig boosters = new BoostersConfig();

    @JsonMerge
    @Valid
    private AdminConfig admin = new AdminConfig();

    @JsonMerge
    @Valid
    private ScoringConfig scoring = new ScoringConfig();

    @JsonMerge
    @Valid
    private ProvablyFairConfig provablyFair = new ProvablyFairConfig();

    @JsonMerge
    @Valid
    private ThemesConfig themes = new ThemesConfig();

    // ── Nested configs ────────────────────────────────────────────────────

    @Data
    public static class MathConfig {

        /** Exponential growth rate α in K(t) = e^(α·t). */
        @Positive
        private double growthRate = 0.065;

        /** House edge fraction [0, 1). E.g. 0.04 = 4%. */
        @DecimalMin("0.0") @DecimalMax("0.5")
        private double houseEdge = 0.08;

        /**
         * Probability of instant crash at {@link #minCrashPoint} [0, 1).
         * Classic: often ~3–8% at 1.00x.
         */
        @DecimalMin("0.0") @DecimalMax("1.0")
        private double instantCrashRate = 0.08;

        /** Floor for crash draw (classic: 1.00). */
        @NotNull @DecimalMin("1.0")
        private BigDecimal minCrashPoint = new BigDecimal("1.00");

        /**
         * Cashout rejected while raw K is below this (classic: 1.01).
         * May be slightly above {@link #minCrashPoint} so instant 1.00 busts before cashout unlocks.
         */
        @NotNull @DecimalMin("1.0")
        private BigDecimal minCashoutMultiplier = new BigDecimal("1.01");
    }

    @Data
    public static class BoostersConfig {

        /** Probability that a booster spawns in a round [0, 1]. */
        @DecimalMin("0.0") @DecimalMax("1.0")
        private double spawnProbability = 0.45;

        /**
         * Line pick bias: triggerLine = floor(roll^power * crashLine).
         * power &gt; 1 favours lower altitude lines.
         */
        @Positive
        private double lineBiasPower = 2.0;

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

        /** Multiplier applied to payout K and to booster points bonus. */
        @NotNull
        @Positive
        private BigDecimal multiplier;

        /** Relative weight for weighted random selection. */
        @Positive
        private int probabilityWeight;
    }

    @Data
    public static class AdminConfig {

        /** Secret key for X-Admin-Key. Bound from YAML/env; never in admin JSON (I7-adjacent). */
        @JsonIgnore
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

        /**
         * Balance granted once when a player is auto-created via GET balance.
         * {@code 0} = create with empty wallet (Hub must deposit). Demo default 1000.
         */
        @NotNull @DecimalMin("0.0")
        private BigDecimal welcomeBalance = new BigDecimal("1000.0");
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

        /**
         * Optional fixed server seed (lowercase hex) for reproducible demos.
         * Honoured only when Spring profile {@code dev} or {@code test} is active (S15).
         * Never set a prod default. Excluded from admin JSON (I7-adjacent).
         */
        @JsonIgnore
        private String devFixedServerSeed;
    }

    @Data
    public static class ThemesConfig {

        @JsonMerge
        @Valid
        private ThemeProfile standard = ThemeProfile.standardDefaults();

        @JsonMerge
        @Valid
        private ThemeProfile lucky = ThemeProfile.luckyDefaults();
    }

    @Data
    public static class ThemeProfile {

        @NotNull @Positive
        private BigDecimal minBetAmount;

        @NotNull @Positive
        private BigDecimal maxBetAmount;

        @Positive
        private double growthRate;

        @NotNull @Positive
        private BigDecimal maxWinMultiplier;

        @Positive
        private int greenLevels;

        @Positive
        private int redLevels;

        public static ThemeProfile standardDefaults() {
            ThemeProfile t = new ThemeProfile();
            t.minBetAmount = new BigDecimal("12.0");
            t.maxBetAmount = new BigDecimal("5000.0");
            t.growthRate = 0.055;
            t.maxWinMultiplier = new BigDecimal("50.0");
            t.greenLevels = 9;
            t.redLevels = 6;
            return t;
        }

        public static ThemeProfile luckyDefaults() {
            ThemeProfile t = new ThemeProfile();
            t.minBetAmount = new BigDecimal("25.0");
            t.maxBetAmount = new BigDecimal("10000.0");
            t.growthRate = 0.085;
            t.maxWinMultiplier = new BigDecimal("100.0");
            t.greenLevels = 9;
            t.redLevels = 12;
            return t;
        }

        public ThemeProfile copy() {
            ThemeProfile t = new ThemeProfile();
            t.minBetAmount = this.minBetAmount;
            t.maxBetAmount = this.maxBetAmount;
            t.growthRate = this.growthRate;
            t.maxWinMultiplier = this.maxWinMultiplier;
            t.greenLevels = this.greenLevels;
            t.redLevels = this.redLevels;
            return t;
        }
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
        m.minCrashPoint = this.math.minCrashPoint;
        m.minCashoutMultiplier = this.math.minCashoutMultiplier;
        copy.math = m;

        BoostersConfig b = new BoostersConfig();
        b.spawnProbability = this.boosters.spawnProbability;
        b.lineBiasPower = this.boosters.lineBiasPower;
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
        a.welcomeBalance = this.admin.welcomeBalance;
        copy.admin = a;

        ProvablyFairConfig pf = new ProvablyFairConfig();
        pf.serverSeedLengthBytes = this.provablyFair.serverSeedLengthBytes;
        pf.hashAlgorithm = this.provablyFair.hashAlgorithm;
        pf.devFixedServerSeed = this.provablyFair.devFixedServerSeed;
        copy.provablyFair = pf;

        ThemesConfig th = new ThemesConfig();
        th.standard = this.themes.standard.copy();
        th.lucky = this.themes.lucky.copy();
        copy.themes = th;

        return copy;
    }

    /**
     * Merges theme profile into this snapshot (mutates). Call once at round start before math.
     */
    public void applyTheme(String balloonType) {
        ThemeProfile profile = "LUCKY".equals(balloonType) ? themes.getLucky() : themes.getStandard();
        admin.setMinBetAmount(profile.getMinBetAmount());
        admin.setMaxBetAmount(profile.getMaxBetAmount());
        admin.setMaxWinMultiplier(profile.getMaxWinMultiplier());
        math.setGrowthRate(profile.getGrowthRate());
        greenLevels = profile.getGreenLevels();
        redLevels = profile.getRedLevels();
    }
}
