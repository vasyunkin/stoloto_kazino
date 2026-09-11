package com.stoloto.balloongame;

import com.stoloto.balloongame.config.GameConfig;
import com.stoloto.balloongame.provablyfair.ProvablyFairService;
import com.stoloto.balloongame.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * S3 — Unit tests for BoosterService and ScoringService.
 * No Spring context, no Docker.
 *
 * Covers mandatory branches:
 *  - instant-crash → no booster (room check)
 *  - spawnProbability = 0.0 → no-spawn always
 *  - spawnProbability = 1.0 → spawn always (when room exists)
 *  - scoring idempotency
 *  - booster bonus calculation
 */
class BoosterScoringTest {

    private static final String SERVER_HEX = "ab".repeat(32);

    private BoosterService  boosterService;
    private ScoringService  scoringService;
    private GameConfig      config;

    @BeforeEach
    void setUp() {
        CrashMathService crashMath = new CrashMathService(new ProvablyFairService());
        boosterService = new BoosterService(crashMath);
        scoringService = new ScoringService();
        config = buildConfig(1.0); // spawnProbability=1.0 by default in tests
    }

    // ── BoosterService tests ──────────────────────────────────────────────

    /**
     * Branch: spawnProbability = 0.0 → booster never spawns regardless of seed.
     * Covers the no-spawn branch (§7.1 requirement).
     */
    @Test
    void rollForRound_noSpawn_whenProbabilityIsZero() {
        GameConfig noSpawnConfig = buildConfig(0.0);
        BigDecimal anyCrash = BigDecimal.valueOf(5.0);

        Optional<BoosterResult> result = boosterService.rollForRound(SERVER_HEX, anyCrash, noSpawnConfig);

        assertThat(result).as("booster must not spawn when spawnProbability=0").isEmpty();
    }

    /**
     * Branch: instant crash (crashPoint=1.0) → lineForMultiplier=0 → no room → no booster.
     * Even with spawnProbability=1.0, there's no room for the booster before crash.
     */
    @Test
    void rollForRound_noBooster_whenInstantCrash() {
        BigDecimal instantCrash = BigDecimal.ONE; // crashPoint = 1.0

        Optional<BoosterResult> result = boosterService.rollForRound(SERVER_HEX, instantCrash, config);

        assertThat(result)
                .as("no booster possible when instant crash (line 0 before crash)")
                .isEmpty();
    }

    /**
     * Branch: spawnProbability=1.0 and crashPoint high → booster spawns.
     */
    @Test
    void rollForRound_spawns_whenProbabilityIsOneAndRoomExists() {
        BigDecimal highCrash = BigDecimal.valueOf(20.0); // lots of lines before crash

        Optional<BoosterResult> result = boosterService.rollForRound(SERVER_HEX, highCrash, config);

        assertThat(result).isPresent();
        BoosterResult br = result.get();
        assertThat(br.tier()).isIn(1, 2, 3);
        assertThat(br.name()).isNotBlank();
        assertThat(br.multiplier()).isGreaterThan(0);
        assertThat(br.triggerLine()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void rollForRound_triggerLine_isBeforeCrashLine() {
        BigDecimal crashPoint = BigDecimal.valueOf(10.0);
        CrashMathService crashMath = new CrashMathService(new ProvablyFairService());
        int crashLine = crashMath.lineForMultiplier(10.0, 0.065, 1.5);

        Optional<BoosterResult> result = boosterService.rollForRound(SERVER_HEX, crashPoint, config);

        result.ifPresent(br ->
            assertThat(br.triggerLine())
                .as("triggerLine must be strictly before crash line")
                .isLessThan(crashLine)
        );
    }

    @Test
    void rollForRound_isDeterministic() {
        BigDecimal crash = BigDecimal.valueOf(10.0);
        Optional<BoosterResult> r1 = boosterService.rollForRound(SERVER_HEX, crash, config);
        Optional<BoosterResult> r2 = boosterService.rollForRound(SERVER_HEX, crash, config);

        assertThat(r1).isEqualTo(r2);
    }

    @Test
    void rollForRound_noTiers_returnsEmpty() {
        GameConfig noTierConfig = buildConfig(1.0);
        noTierConfig.getBoosters().setTiers(List.of());
        BigDecimal crash = BigDecimal.valueOf(10.0);

        Optional<BoosterResult> result = boosterService.rollForRound(SERVER_HEX, crash, noTierConfig);

        assertThat(result).isEmpty();
    }

    // ── BoosterService.hashToUnitInterval ────────────────────────────────

    @Test
    void hashToUnitInterval_inRange() {
        double u = BoosterService.hashToUnitInterval("test-input");
        assertThat(u).isBetween(0.0, 1.0);
    }

    @Test
    void hashToUnitInterval_isDeterministic() {
        double u1 = BoosterService.hashToUnitInterval("same");
        double u2 = BoosterService.hashToUnitInterval("same");
        assertThat(u1).isEqualTo(u2);
    }

    @Test
    void hashToUnitInterval_differentInputs_differentOutputs() {
        double u1 = BoosterService.hashToUnitInterval("input-a");
        double u2 = BoosterService.hashToUnitInterval("input-b");
        assertThat(u1).isNotEqualTo(u2);
    }

    // ── ScoringService tests ──────────────────────────────────────────────

    @Test
    void pointsForNewLines_noNewLines_returnsZero() {
        assertThat(scoringService.pointsForNewLines(5, 5, config)).isEqualTo(0);
        assertThat(scoringService.pointsForNewLines(5, 3, config)).isEqualTo(0);
    }

    @Test
    void pointsForNewLines_singleNewLine_greenZone() {
        // Line 1 is GREEN (greenLevels=9), pointsPerLine=10
        int points = scoringService.pointsForNewLines(0, 1, config);
        assertThat(points).isEqualTo(10);
    }

    @Test
    void pointsForNewLines_threeGreenLines() {
        int points = scoringService.pointsForNewLines(2, 5, config);
        // Lines 3, 4, 5 = 3 lines × 10 pts
        assertThat(points).isEqualTo(30);
    }

    @Test
    void pointsForNewLines_redZone_defaultMultiplierOnePointZero() {
        // Lines 9-20 are RED with multiplier 1.0 (default, same as green)
        int points = scoringService.pointsForNewLines(8, 11, config);
        // Lines 9, 10, 11 = 3 lines × 10 pts × 1.0
        assertThat(points).isEqualTo(30);
    }

    /**
     * Idempotency: calling pointsForNewLines for the same range twice gives 0 on second call.
     * This simulates the polling pattern where previousLines tracks progress.
     */
    @Test
    void pointsForNewLines_redZone_usesConfigMultiplier() {
        config.getScoring().setRedZoneMultiplier(2.0);
        // Lines 9, 10, 11 are RED (greenLevels=9)
        int points = scoringService.pointsForNewLines(8, 11, config);
        assertThat(points).isEqualTo(60); // 3 × 10 × 2.0
    }

    @Test
    void pointsForNewLines_idempotent_onRepeatCallWithSameWindow() {
        int firstCall  = scoringService.pointsForNewLines(0, 5, config);
        int secondCall = scoringService.pointsForNewLines(5, 5, config); // same upper bound

        assertThat(firstCall).isEqualTo(50); // 5 lines × 10 pts
        assertThat(secondCall).isEqualTo(0); // no NEW lines
    }

    @Test
    void boosterBonus_correctForTier1Multiplier() {
        // Tier 1: multiplier 1.5, pointsPerLine=10 → bonus = round(10 × 1.5) = 15
        BoosterResult booster = new BoosterResult(1, "Standard Flame", 1.5, 3);
        assertThat(scoringService.boosterBonus(booster, config)).isEqualTo(15);
    }

    @Test
    void boosterBonus_correctForTier3Multiplier() {
        // Tier 3: multiplier 3.0, pointsPerLine=10 → bonus = 30
        BoosterResult booster = new BoosterResult(3, "Nitro Thruster", 3.0, 5);
        assertThat(scoringService.boosterBonus(booster, config)).isEqualTo(30);
    }

    @Test
    void isBoosterActivating_activatesWhenLineReached() {
        BoosterResult booster = new BoosterResult(1, "Flame", 1.5, 5);
        assertThat(scoringService.isBoosterActivating(booster, 4, false)).isFalse();
        assertThat(scoringService.isBoosterActivating(booster, 5, false)).isTrue();
        assertThat(scoringService.isBoosterActivating(booster, 6, false)).isTrue();
    }

    @Test
    void isBoosterActivating_notTwice_whenAlreadyActive() {
        BoosterResult booster = new BoosterResult(1, "Flame", 1.5, 5);
        assertThat(scoringService.isBoosterActivating(booster, 5, true)).isFalse();
    }

    @Test
    void isBoosterActivating_falseForNullBooster() {
        assertThat(scoringService.isBoosterActivating(null, 10, false)).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private GameConfig buildConfig(double spawnProbability) {
        GameConfig cfg = new GameConfig();
        cfg.setGreenLevels(9);
        cfg.setRedLevels(12);
        cfg.setPointsPerLine(10);
        cfg.setAscentSpeedLinesPerSec(1.5);

        GameConfig.MathConfig math = new GameConfig.MathConfig();
        math.setGrowthRate(0.065);
        math.setHouseEdge(0.04);
        math.setInstantCrashRate(0.03);
        cfg.setMath(math);

        GameConfig.AdminConfig admin = new GameConfig.AdminConfig();
        admin.setMaxWinMultiplier(BigDecimal.valueOf(100.0));
        cfg.setAdmin(admin);

        GameConfig.BoostersConfig bc = new GameConfig.BoostersConfig();
        bc.setSpawnProbability(spawnProbability);
        bc.setTiers(List.of(
                makeTier(1, "Standard Flame", 1.5, 50),
                makeTier(2, "Super Burner",   2.0, 35),
                makeTier(3, "Nitro Thruster",  3.0, 15)
        ));
        cfg.setBoosters(bc);

        return cfg;
    }

    private GameConfig.BoosterTier makeTier(int tier, String name, double mult, int weight) {
        GameConfig.BoosterTier t = new GameConfig.BoosterTier();
        t.setTier(tier);
        t.setName(name);
        t.setMultiplier(mult);
        t.setProbabilityWeight(weight);
        return t;
    }
}
