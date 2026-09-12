package com.stoloto.balloongame.service;

import com.stoloto.balloongame.config.GameConfig;
import com.stoloto.balloongame.provablyfair.ProvablyFairService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Pure crash-game mathematics — algorithm {@code crash-v1}.
 *
 * <h3>Multiplier over time (I3 — single function):</h3>
 * <pre>
 *   K(t) = e^(r · t)       K(0) = 1, monotonically increasing
 *   t in seconds, r = game.math.growth-rate (default 0.065)
 * </pre>
 *
 * <h3>Crash-point derivation (§6.1):</h3>
 * <pre>
 *   h = HMAC-SHA256(serverSeedBytes, clientSeed + ":" + nonce)
 *   u = first52bits(h) / 2^52          // ∈ [0, 1)
 *   floor = minCrashPoint (default 1.20)
 *   if u &lt; instantCrashRate → crashPoint = floor
 *   else crashPoint = min(maxWin, max(floor, (1 - houseEdge) / u))
 * </pre>
 *
 * <p><b>I2:</b> crash point is drawn once at round start and never recalculated.
 * <p><b>I3:</b> {@link #multiplierAt} is the single multiplier function used by
 *              {@code GameClockService}, {@code state} and {@code cashout} paths.
 * <p><b>I8:</b> all methods take a {@code GameConfig} parameter (the round's
 *              {@code configSnapshot}) — they never read the live {@code @ConfigurationProperties} bean.
 */
@Service
@RequiredArgsConstructor
public class CrashMathService {

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final int SCALE = 4;

    private final ProvablyFairService provablyFairService;

    // ── Multiplier over time ──────────────────────────────────────────────

    /**
     * K(t) = e^(r · t) where t = now - startTime in seconds.
     *
     * <p>Use this from {@code GameClockService} only; never call it twice for the
     * same logical moment (I3). Pass {@code configSnapshot.math.growthRate}.
     *
     * @param startTime  when the round started (from {@code GameSession})
     * @param now        current server time (from the single {@code Clock} bean)
     * @param growthRate {@code r} from {@code configSnapshot.math.growthRate}
     * @return K with scale 4, HALF_UP
     */
    public BigDecimal multiplierAt(Instant startTime, Instant now, double growthRate) {
        double t = (now.toEpochMilli() - startTime.toEpochMilli()) / 1000.0;
        if (t < 0) t = 0; // guard against clock skew
        double k = Math.exp(growthRate * t);
        return BigDecimal.valueOf(k).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Inverse of K(t): given multiplier K, returns the elapsed time in seconds.
     * <pre>
     *   t = ln(K) / r
     * </pre>
     */
    public double timeAtMultiplier(double multiplier, double growthRate) {
        if (multiplier <= 1.0) return 0.0;
        return Math.log(multiplier) / growthRate;
    }

    // ── Line index ────────────────────────────────────────────────────────

    /**
     * Current line index: floor(t · linesPerSec).
     * Line 0 = ground, line 1 = first altitude line crossed.
     *
     * @param startTime      when the round started
     * @param now            current server time
     * @param linesPerSec    {@code configSnapshot.ascentSpeedLinesPerSec}
     * @return 0-based line index
     */
    public int lineIndexAt(Instant startTime, Instant now, double linesPerSec) {
        double t = (now.toEpochMilli() - startTime.toEpochMilli()) / 1000.0;
        if (t < 0) return 0;
        return (int) (t * linesPerSec);
    }

    /**
     * Line index at which the given multiplier is reached.
     * Useful for determining on which line a booster triggers or the balloon crashes.
     *
     * @param multiplier the target multiplier K
     * @param growthRate {@code r} from configSnapshot
     * @param linesPerSec {@code ascentSpeedLinesPerSec} from configSnapshot
     * @return 0-based line index (0 = instant/ground crash)
     */
    public int lineForMultiplier(double multiplier, double growthRate, double linesPerSec) {
        double t = timeAtMultiplier(multiplier, growthRate);
        return (int) (t * linesPerSec);
    }

    /**
     * Zone classification for a given line index.
     *
     * @param lineIndex  current line
     * @param greenLines {@code configSnapshot.greenLevels}
     * @param redLines   {@code configSnapshot.redLevels}
     * @return "GREEN", "RED", or "CRASHED"
     */
    public String zoneFor(int lineIndex, int greenLines, int redLines) {
        if (lineIndex < greenLines) return "GREEN";
        if (lineIndex < greenLines + redLines) return "RED";
        return "CRASHED";
    }

    // ── Crash point draw ─────────────────────────────────────────────────

    /**
     * Draws the crash multiplier from HMAC — deterministic given the seeds.
     *
     * <p>Algorithm (§6.1, crash-v1):
     * <ol>
     *   <li>{@code h = HMAC-SHA256(serverSeedBytes, (clientSeed + ":" + nonce).bytes)}</li>
     *   <li>{@code u = first52bits(h) / 2^52}  — unit interval [0, 1)</li>
     *   <li>if {@code u < instantCrashRate} → {@code crashPoint = minCrashPoint}</li>
     *   <li>else {@code crashPoint = min(maxWin, max(minCrashPoint, (1 - houseEdge) / u))}</li>
     * </ol>
     *
     * <p><b>I2:</b> call exactly once at round start; result stored in DB.
     *
     * @param serverSeedHex hex-encoded server seed (as stored in DB / {@link com.stoloto.balloongame.provablyfair.RoundSecrets})
     * @param clientSeed    player seed (empty string if none)
     * @param nonce         round nonce
     * @param config        the round's immutable {@code configSnapshot}
     * @return crash multiplier, scale 4, HALF_UP; always ≥ {@code minCrashPoint}
     */
    public BigDecimal drawCrashMultiplier(String serverSeedHex, String clientSeed,
                                          long nonce, GameConfig config) {
        byte[] serverSeedBytes = provablyFairService.decodeServerSeed(serverSeedHex);
        String message = clientSeed + ":" + nonce;
        byte[] hmac = provablyFairService.hmacSha256(
                serverSeedBytes, message.getBytes(StandardCharsets.UTF_8));

        double u = extractU(hmac);
        return computeCrashPoint(u, config);
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    /**
     * Extracts u = first52bits(hmac) / 2^52 ∈ [0, 1).
     * If u == 0.0 (extremely rare), returns a tiny positive value to avoid ÷0.
     */
    public double extractU(byte[] hmac) {
        // Assemble top 7 bytes (56 bits) as a long, then shift right 4 to get 52 bits
        long top7 = 0L;
        for (int i = 0; i < 7; i++) {
            top7 = (top7 << 8) | (hmac[i] & 0xFFL);
        }
        long first52 = top7 >>> 4;   // unsigned right shift discards low 4 bits
        double u = (double) first52 / (double) (1L << 52);
        return u == 0.0 ? Double.MIN_VALUE : u;
    }

    /**
     * Applies the crash-point formula given u ∈ (0, 1).
     * Never returns below {@code math.minCrashPoint} (default 1.20).
     */
    public BigDecimal computeCrashPoint(double u, GameConfig config) {
        GameConfig.MathConfig math = config.getMath();
        BigDecimal floor = math.getMinCrashPoint() != null ? math.getMinCrashPoint() : ONE;
        double floorD = floor.doubleValue();
        double maxWin = config.getAdmin().getMaxWinMultiplier().doubleValue();
        if (maxWin < floorD) {
            maxWin = floorD;
        }

        if (u < math.getInstantCrashRate()) {
            return floor.setScale(SCALE, RoundingMode.HALF_UP);
        }

        double raw = (1.0 - math.getHouseEdge()) / u;
        double capped = Math.min(maxWin, Math.max(floorD, raw));
        return BigDecimal.valueOf(capped).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
