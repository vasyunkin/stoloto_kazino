package com.stoloto.balloongame.provablyfair;

import com.stoloto.balloongame.config.GameConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Provably Fair helpers — algorithm version {@code crash-v1}.
 *
 * <h3>Commit (before round):</h3>
 * <pre>
 *   commitHash = SHA-256( serverSeedHex + "|" + clientSeed + "|" + nonce )
 * </pre>
 * Sent to the client in {@code StartGameResponse} before flight starts.
 *
 * <h3>HMAC for crash point (internal, §6.1):</h3>
 * <pre>
 *   h = HMAC-SHA256( key=serverSeedBytes, msg=(clientSeed + ":" + nonce) )
 *   u = first52bits(h) / 2^52          // ∈ [0, 1)
 * </pre>
 *
 * <h3>Reveal (after terminal status):</h3>
 * {@code GET /api/game/verify/{gameId}} returns serverSeedHex so the client
 * can independently reproduce commitHash and crashPoint (§7).
 *
 * <p><b>I7 compliance:</b> serverSeedHex must not appear in logs or API responses
 * until the round reaches CRASHED or CASHED_OUT. {@code RoundSecrets} is never
 * serialised directly to HTTP; controllers use separate DTOs.
 *
 * <p><b>S15:</b> optional {@code game.provably-fair.dev-fixed-server-seed} is used only
 * when Spring profile {@code dev} or {@code test} is active.
 */
@Slf4j
@Service
public class ProvablyFairService {

    private static final HexFormat HEX = HexFormat.of();
    private static final String ALGORITHM_VERSION = "crash-v1";

    private final Environment environment;

    /** Unit-test constructor — fixed seed never applied without an {@link Environment}. */
    public ProvablyFairService() {
        this(null);
    }

    @Autowired
    public ProvablyFairService(Environment environment) {
        this.environment = environment;
    }

    /** @return {@code "crash-v1"} — stable identifier for PF audit. */
    public String algorithmVersion() {
        return ALGORITHM_VERSION;
    }

    // ── Secret generation ─────────────────────────────────────────────────

    /**
     * Generates a fresh set of {@link RoundSecrets} for a new round.
     *
     * @param clientSeed player-supplied seed (may be empty string if not provided)
     * @param nonce      per-player counter or global sequence value
     * @param pfConfig   from the round's {@code configSnapshot} (I8)
     * @return immutable secrets; {@code serverSeedHex} must be stored in DB immediately (§5.1)
     */
    public RoundSecrets generateSecrets(String clientSeed, long nonce,
                                        GameConfig.ProvablyFairConfig pfConfig) {
        String hex = resolveServerSeedHex(pfConfig);
        String commit = computeCommitHash(hex, clientSeed, nonce, pfConfig.getHashAlgorithm());
        return new RoundSecrets(hex, clientSeed, nonce, commit);
    }

    /**
     * Resolve server seed: fixed hex when allowed by profile, otherwise SecureRandom.
     */
    String resolveServerSeedHex(GameConfig.ProvablyFairConfig pfConfig) {
        String fixed = pfConfig.getDevFixedServerSeed();
        if (fixed != null && !fixed.isBlank()) {
            if (!fixedSeedProfilesActive()) {
                throw new IllegalStateException(
                        "game.provably-fair.dev-fixed-server-seed is set but active profiles "
                                + "are not dev/test — refusing to start rounds with a fixed seed");
            }
            String normalized = fixed.trim().toLowerCase(Locale.ROOT);
            int expectedLen = pfConfig.getServerSeedLengthBytes() * 2;
            if (normalized.length() != expectedLen || !normalized.matches("[0-9a-f]+")) {
                throw new IllegalStateException(
                        "dev-fixed-server-seed must be " + expectedLen
                                + " lowercase hex chars (got length " + normalized.length() + ")");
            }
            return normalized;
        }
        byte[] raw = generateServerSeedBytes(pfConfig.getServerSeedLengthBytes());
        return HEX.formatHex(raw);
    }

    boolean fixedSeedProfilesActive() {
        if (environment == null) {
            return false;
        }
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "dev".equals(p) || "test".equals(p));
    }

    /**
     * Generate cryptographically secure random bytes for a server seed.
     */
    public byte[] generateServerSeedBytes(int lengthBytes) {
        byte[] seed = new byte[lengthBytes];
        new SecureRandom().nextBytes(seed);
        return seed;
    }

    // ── Commit hash ───────────────────────────────────────────────────────

    /**
     * Computes the commit hash that is given to the client before the round:
     * <pre>
     *   SHA-256( serverSeedHex + "|" + clientSeed + "|" + nonce )
     * </pre>
     *
     * @param serverSeedHex lowercase hex string (as stored in DB)
     * @param clientSeed    player seed (empty string if none)
     * @param nonce         round counter
     * @param hashAlgorithm e.g. "SHA-256" from pfConfig
     * @return lowercase hex of the hash
     */
    public String computeCommitHash(String serverSeedHex, String clientSeed, long nonce,
                                    String hashAlgorithm) {
        String preimage = serverSeedHex + "|" + clientSeed + "|" + nonce;
        return sha(preimage.getBytes(StandardCharsets.UTF_8), hashAlgorithm);
    }

    // ── HMAC for crash point (used by CrashMathService) ──────────────────

    /**
     * Computes HMAC-SHA256 over the message using serverSeedBytes as the key.
     * Used by {@link com.stoloto.balloongame.service.CrashMathService} to derive {@code u}.
     *
     * @param serverSeedBytes raw server seed bytes (decoded from hex)
     * @param message         UTF-8 bytes of {@code clientSeed + ":" + nonce}
     * @return 32-byte HMAC digest
     */
    public byte[] hmacSha256(byte[] serverSeedBytes, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(serverSeedBytes, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 failed — JDK must support it", e);
        }
    }

    // ── Verification (used in GET /verify — S7) ───────────────────────────

    /**
     * Verifies that {@code commitHash} matches the recomputed hash for the given seeds.
     * Called from the verify endpoint after the round ends.
     *
     * @return {@code true} if the commit is authentic
     */
    public boolean verify(String serverSeedHex, String clientSeed, long nonce,
                          String commitHash, String hashAlgorithm) {
        String expected = computeCommitHash(serverSeedHex, clientSeed, nonce, hashAlgorithm);
        return expected.equalsIgnoreCase(commitHash);
    }

    // ── Utility ───────────────────────────────────────────────────────────

    /**
     * Hashes the input bytes using the given algorithm and returns lowercase hex.
     */
    public String sha(byte[] input, String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            return HEX.formatHex(md.digest(input));
        } catch (Exception e) {
            throw new IllegalStateException("Hash algorithm not available: " + algorithm, e);
        }
    }

    /**
     * Converts a hex-encoded server seed string to raw bytes.
     * (Reverse of {@link HexFormat#formatHex})
     */
    public byte[] decodeServerSeed(String serverSeedHex) {
        return HEX.parseHex(serverSeedHex);
    }
}
