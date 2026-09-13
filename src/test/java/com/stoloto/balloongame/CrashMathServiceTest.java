package com.stoloto.balloongame;

import com.stoloto.balloongame.config.GameConfig;
import com.stoloto.balloongame.provablyfair.ProvablyFairService;
import com.stoloto.balloongame.service.CrashMathService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.*;

/**
 * S3 — Unit tests for CrashMathService.
 *
 * <h3>Golden vector strategy:</h3>
 * Expected crash points are computed INDEPENDENTLY in each test using raw JDK
 * {@link Mac} — not by calling the service. This ensures the tests are genuine
 * regression guards and not tautologies.
 *
 * <h3>Three mandatory vectors (§7.1):</h3>
 * <ol>
 *   <li>Normal crash  — default config, u &gt; instantCrashRate, result ∈ (1, maxWin)</li>
 *   <li>Instant crash — config.instantCrashRate = 1.0 → always 1.0000</li>
 *   <li>Cap at maxWin — config.maxWinMultiplier = 1.01 → result capped</li>
 * </ol>
 *
 * Algorithm version: crash-v1
 */
class CrashMathServiceTest {

    // Fixed seeds for determinism
    private static final String SERVER_HEX = "ab".repeat(32); // 32 bytes 0xAB
    private static final String CLIENT     = "player-test";
    private static final long   NONCE      = 42L;

    private CrashMathService crashMath;
    private GameConfig        defaultConfig;

    @BeforeEach
    void setUp() {
        crashMath     = new CrashMathService(new ProvablyFairService());
        defaultConfig = buildConfig(0.03, 0.04, 100.0); // default: 3% instant, 4% house, cap 100x
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private GameConfig buildConfig(double instantRate, double houseEdge, double maxWin) {
        GameConfig cfg = new GameConfig();
        GameConfig.MathConfig math = new GameConfig.MathConfig();
        math.setInstantCrashRate(instantRate);
        math.setHouseEdge(houseEdge);
        math.setMinCrashPoint(new BigDecimal("1.00"));
        math.setMinCashoutMultiplier(new BigDecimal("1.01"));
        cfg.setMath(math);

        GameConfig.AdminConfig admin = new GameConfig.AdminConfig();
        admin.setMaxWinMultiplier(BigDecimal.valueOf(maxWin));
        cfg.setAdmin(admin);

        return cfg;
    }

    /** Independent reference: compute HMAC-SHA256 using raw JDK. */
    private byte[] computeHmacRef(String serverHex, String message) throws Exception {
        byte[] key = HexFormat.of().parseHex(serverHex);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    }

    /** Extract u = first52bits / 2^52 from HMAC bytes (reference implementation). */
    private double extractURef(byte[] hmac) {
        long top7 = 0L;
        for (int i = 0; i < 7; i++) top7 = (top7 << 8) | (hmac[i] & 0xFFL);
        long first52 = top7 >>> 4;
        double u = (double) first52 / (double) (1L << 52);
        return u == 0.0 ? Double.MIN_VALUE : u;
    }

    // ── Golden vector 1: Normal crash ─────────────────────────────────────

    /**
     * Golden vector 1: serverSeed="abab...ab", clientSeed="player-test", nonce=42.
     * Expected crashPoint computed independently from raw HMAC-SHA256.
     * With default config (instantCrashRate=0.03, houseEdge=0.04, maxWin=100):
     * result ∈ [1.0, 100.0].
     */
    @Test
    void drawCrashMultiplier_goldenVector_normalCrash() throws Exception {
        byte[] hmacRef = computeHmacRef(SERVER_HEX, CLIENT + ":" + NONCE);
        double u = extractURef(hmacRef);

        // Service output
        BigDecimal actual = crashMath.drawCrashMultiplier(SERVER_HEX, CLIENT, NONCE, defaultConfig);

        if (u < 0.03) {
            // This seed happens to produce instant crash → floor at minCrashPoint
            assertThat(actual).isEqualByComparingTo("1.0000");
        } else {
            // Normal crash — verify against independent formula with floor 1.00
            double raw  = (1.0 - 0.04) / u;
            double cap  = Math.min(100.0, Math.max(1.0, raw));
            BigDecimal expected = BigDecimal.valueOf(cap).setScale(4, RoundingMode.HALF_UP);
            assertThat(actual)
                    .as("crash point must match independent HMAC formula (golden vector 1)")
                    .isEqualByComparingTo(expected);
        }

        // Common invariants regardless of branch
        assertThat(actual.scale()).isEqualTo(4);
        assertThat(actual).isGreaterThanOrEqualTo(BigDecimal.ONE);
        assertThat(actual).isLessThanOrEqualTo(BigDecimal.valueOf(100));
    }

    @Test
    void drawCrashMultiplier_isDeterministic() {
        BigDecimal r1 = crashMath.drawCrashMultiplier(SERVER_HEX, CLIENT, NONCE, defaultConfig);
        BigDecimal r2 = crashMath.drawCrashMultiplier(SERVER_HEX, CLIENT, NONCE, defaultConfig);
        assertThat(r1).isEqualByComparingTo(r2);
    }

    @Test
    void drawCrashMultiplier_differentSeedsGiveDifferentResults() {
        String other = "cd".repeat(32);
        BigDecimal r1 = crashMath.drawCrashMultiplier(SERVER_HEX, CLIENT, NONCE, defaultConfig);
        BigDecimal r2 = crashMath.drawCrashMultiplier(other, CLIENT, NONCE, defaultConfig);
        // Very high probability of different results (though technically not guaranteed)
        assertThat(r1).isNotEqualByComparingTo(r2);
    }

    // ── Golden vector 2: Instant crash ────────────────────────────────────

    /**
     * Golden vector 2: instantCrashRate = 1.0 → ANY seed always produces minCrashPoint (1.00).
     */
    @Test
    void drawCrashMultiplier_goldenVector_instantCrash_whenRateIs100Percent() {
        GameConfig instantConfig = buildConfig(1.0, 0.04, 100.0);

        BigDecimal crash1 = crashMath.drawCrashMultiplier(SERVER_HEX, CLIENT, NONCE, instantConfig);
        BigDecimal crash2 = crashMath.drawCrashMultiplier("00".repeat(32), "other", 1L, instantConfig);
        BigDecimal crash3 = crashMath.drawCrashMultiplier("ff".repeat(32), "", 0L, instantConfig);

        assertThat(crash1).as("instant-crash: seed1").isEqualByComparingTo("1.0000");
        assertThat(crash2).as("instant-crash: seed2").isEqualByComparingTo("1.0000");
        assertThat(crash3).as("instant-crash: seed3").isEqualByComparingTo("1.0000");
    }

    /**
     * Golden vector 2b: instantCrashRate = 0.0 → crash point is always >= minCrashPoint
     */
    @Test
    void drawCrashMultiplier_noInstantCrash_whenRateIsZero() {
        GameConfig noInstant = buildConfig(0.0, 0.04, 100.0);
        BigDecimal result = crashMath.drawCrashMultiplier(SERVER_HEX, CLIENT, NONCE, noInstant);
        assertThat(result).isGreaterThanOrEqualTo(BigDecimal.ONE);
    }

    // ── Golden vector 3: Cap at maxWinMultiplier ──────────────────────────

    /**
     * Golden vector 3: maxWinMultiplier = 1.25 with minCrash = 1.00, no instant crash.
     */
    @Test
    void drawCrashMultiplier_goldenVector_cappedAtMaxWin() {
        GameConfig capConfig = buildConfig(0.0, 0.01, 1.25);

        BigDecimal result = crashMath.drawCrashMultiplier(SERVER_HEX, CLIENT, NONCE, capConfig);

        assertThat(result)
                .as("result must not exceed maxWinMultiplier")
                .isLessThanOrEqualTo(BigDecimal.valueOf(1.25));
        assertThat(result).isGreaterThanOrEqualTo(BigDecimal.ONE);
    }

    @Test
    void drawCrashMultiplier_alwaysInRange() {
        for (int i = 0; i < 20; i++) {
            String seed = String.format("%02x", i).repeat(32);
            BigDecimal result = crashMath.drawCrashMultiplier(seed, CLIENT, (long) i, defaultConfig);
            assertThat(result).isBetween(BigDecimal.ONE, BigDecimal.valueOf(100));
        }
    }

    // ── extractU ─────────────────────────────────────────────────────────

    @Test
    void extractU_allZeroBytes_returnsMinValue() {
        byte[] zeros = new byte[32];
        double u = crashMath.extractU(zeros);
        assertThat(u).isGreaterThan(0.0); // must not return 0 (guard)
    }

    @Test
    void extractU_allOneBytes_returnsNearOne() {
        byte[] ones = new byte[32];
        java.util.Arrays.fill(ones, (byte) 0xFF);
        double u = crashMath.extractU(ones);
        assertThat(u).isLessThan(1.0).isGreaterThan(0.99);
    }

    // ── multiplierAt ─────────────────────────────────────────────────────

    @Test
    void multiplierAt_atStart_isOne() {
        Instant t = Instant.now();
        BigDecimal k = crashMath.multiplierAt(t, t, 0.065);
        assertThat(k).isEqualByComparingTo("1.0000");
    }

    @Test
    void multiplierAt_after10Seconds_isCorrect() {
        Instant start = Instant.EPOCH;
        Instant now   = Instant.EPOCH.plusSeconds(10);
        // K = e^(0.065 * 10) = e^0.65 ≈ 1.9155
        BigDecimal k = crashMath.multiplierAt(start, now, 0.065);
        assertThat(k).isBetween(BigDecimal.valueOf(1.9), BigDecimal.valueOf(1.95));
    }

    @Test
    void multiplierAt_negativeTime_returnsOne() {
        Instant start = Instant.now().plusSeconds(10); // start is in the future
        Instant now   = Instant.now();
        BigDecimal k = crashMath.multiplierAt(start, now, 0.065);
        assertThat(k).isEqualByComparingTo("1.0000");
    }

    // ── timeAtMultiplier ─────────────────────────────────────────────────

    @Test
    void timeAtMultiplier_invertsMultiplierAt() {
        double r = 0.065;
        double K = 5.0;
        double t = crashMath.timeAtMultiplier(K, r);

        Instant start = Instant.EPOCH;
        Instant point = Instant.EPOCH.plusNanos((long) (t * 1e9));
        BigDecimal k  = crashMath.multiplierAt(start, point, r);

        // Should round-trip to ~5.0 (within scale-4 rounding)
        assertThat(k).isBetween(BigDecimal.valueOf(4.99), BigDecimal.valueOf(5.01));
    }

    @Test
    void timeAtMultiplier_forMultiplierOne_isZero() {
        assertThat(crashMath.timeAtMultiplier(1.0, 0.065)).isEqualTo(0.0);
    }

    // ── lineIndexAt ──────────────────────────────────────────────────────

    @Test
    void lineIndexAt_atStart_isZero() {
        Instant t = Instant.EPOCH;
        assertThat(crashMath.lineIndexAt(t, t, 1.5)).isEqualTo(0);
    }

    @Test
    void lineIndexAt_after4Seconds_isCorrect() {
        Instant start = Instant.EPOCH;
        Instant now   = Instant.EPOCH.plusSeconds(4);
        // 4s × 1.5 lines/s = 6 lines (floor)
        assertThat(crashMath.lineIndexAt(start, now, 1.5)).isEqualTo(6);
    }

    // ── lineForMultiplier ────────────────────────────────────────────────

    @Test
    void lineForMultiplier_instantCrash_isZero() {
        // K=1.0 → t=0 → line=0
        assertThat(crashMath.lineForMultiplier(1.0, 0.065, 1.5)).isEqualTo(0);
    }

    @Test
    void lineForMultiplier_isConsistentWithLineIndexAt() {
        double K = 3.5;
        double r = 0.065;
        double lps = 1.5;

        double t = crashMath.timeAtMultiplier(K, r);
        Instant start = Instant.EPOCH;
        Instant point = Instant.EPOCH.plusNanos((long) (t * 1e9));

        int lineFromMultiplier = crashMath.lineForMultiplier(K, r, lps);
        int lineFromTime       = crashMath.lineIndexAt(start, point, lps);

        assertThat(lineFromMultiplier).isEqualTo(lineFromTime);
    }

    // ── zoneFor ──────────────────────────────────────────────────────────

    @Test
    void zoneFor_greenRange() {
        assertThat(crashMath.zoneFor(0, 9, 12)).isEqualTo("GREEN");
        assertThat(crashMath.zoneFor(8, 9, 12)).isEqualTo("GREEN");
    }

    @Test
    void zoneFor_redRange() {
        assertThat(crashMath.zoneFor(9,  9, 12)).isEqualTo("RED");
        assertThat(crashMath.zoneFor(20, 9, 12)).isEqualTo("RED");
    }

    @Test
    void zoneFor_crashedBeyondAllLines() {
        assertThat(crashMath.zoneFor(21, 9, 12)).isEqualTo("CRASHED");
    }
}
