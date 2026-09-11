package com.stoloto.balloongame.service;

import com.stoloto.balloongame.config.GameConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;

/**
 * Determines whether a booster spawns for a given round and, if so, which tier
 * and trigger line — all derived deterministically from {@code serverSeedHex}.
 *
 * <h3>Algorithm (§6.2, crash-v1):</h3>
 * <ol>
 *   <li>Spawn check: {@code p = hash(serverSeedHex + ":booster:spawn")} → unit interval.
 *       If {@code p ≥ spawnProbability} → no booster.</li>
 *   <li>Room check: if the crash happens at or before line 0, there is no room for a booster.</li>
 *   <li>Tier selection: weighted random from config tiers using
 *       {@code hash(serverSeedHex + ":booster:tier")}.</li>
 *   <li>Trigger line: {@code hash(serverSeedHex + ":booster:line")} → random line in
 *       {@code [0, crashLine)} — strictly before crash.</li>
 * </ol>
 *
 * <p><b>I2:</b> {@link #rollForRound} is called exactly once at round start; result is stored
 * in {@code GameSession} and {@code game_round.boost_*} columns.
 * <p><b>I8:</b> all config reads use the passed {@code configSnapshot}, not the live bean.
 */
@Service
@RequiredArgsConstructor
public class BoosterService {

    private final CrashMathService crashMathService;

    /**
     * Rolls for a booster for the given round.
     *
     * @param serverSeedHex hex-encoded server seed (as stored in DB)
     * @param crashPoint    the round's crash multiplier (from {@link CrashMathService#drawCrashMultiplier})
     * @param config        the round's immutable {@code configSnapshot}
     * @return {@link BoosterResult} if a booster spawned, or {@code Optional.empty()}
     */
    public Optional<BoosterResult> rollForRound(String serverSeedHex, BigDecimal crashPoint,
                                                GameConfig config) {
        GameConfig.BoostersConfig bc = config.getBoosters();
        if (bc.getTiers() == null || bc.getTiers().isEmpty()) {
            return Optional.empty();
        }

        // Step 1: spawn probability check
        double spawnRoll = hashToUnitInterval(serverSeedHex + ":booster:spawn");
        if (spawnRoll >= bc.getSpawnProbability()) {
            return Optional.empty(); // booster does not spawn this round
        }

        // Step 2: room check — booster must trigger before crash line
        int crashLine = crashMathService.lineForMultiplier(
                crashPoint.doubleValue(),
                config.getMath().getGrowthRate(),
                config.getAscentSpeedLinesPerSec());
        if (crashLine <= 0) {
            // No altitude lines before crash (instant crash or very early crash)
            return Optional.empty();
        }

        // Step 3: weighted tier selection
        GameConfig.BoosterTier tier = selectTier(
                hashToUnitInterval(serverSeedHex + ":booster:tier"), bc.getTiers());

        // Step 4: trigger line — uniformly random in [0, crashLine)
        double lineRoll = hashToUnitInterval(serverSeedHex + ":booster:line");
        int triggerLine = (int) (lineRoll * crashLine); // [0, crashLine - 1] inclusive

        return Optional.of(new BoosterResult(
                tier.getTier(), tier.getName(), tier.getMultiplier(), triggerLine));
    }

    // ── Internals ─────────────────────────────────────────────────────────

    /**
     * Hashes the input string with SHA-256 and returns the first 52 bits as a unit interval.
     * Deterministic: same input → same output, always ∈ [0, 1).
     */
    public static double hashToUnitInterval(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));

            long top7 = 0L;
            for (int i = 0; i < 7; i++) {
                top7 = (top7 << 8) | (digest[i] & 0xFFL);
            }
            long first52 = top7 >>> 4;
            return (double) first52 / (double) (1L << 52);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Weighted random tier selection.
     * The {@code roll} (∈ [0, 1)) is scaled to the total weight and used to pick a tier.
     */
    public static GameConfig.BoosterTier selectTier(double roll, List<GameConfig.BoosterTier> tiers) {
        int totalWeight = tiers.stream().mapToInt(GameConfig.BoosterTier::getProbabilityWeight).sum();
        double target = roll * totalWeight;
        double cumulative = 0;
        for (GameConfig.BoosterTier t : tiers) {
            cumulative += t.getProbabilityWeight();
            if (target < cumulative) {
                return t;
            }
        }
        // Fallback (should not happen with well-defined weights): return last tier
        return tiers.get(tiers.size() - 1);
    }
}
