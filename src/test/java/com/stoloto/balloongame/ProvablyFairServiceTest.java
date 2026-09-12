package com.stoloto.balloongame;

import com.stoloto.balloongame.config.GameConfig;
import com.stoloto.balloongame.provablyfair.ProvablyFairService;
import com.stoloto.balloongame.provablyfair.RoundSecrets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.*;

/**
 * S3 — Unit tests for ProvablyFairService.
 *
 * Golden vectors are verified by computing expected values INDEPENDENTLY
 * via raw JDK {@link MessageDigest} — not by calling the service twice.
 * This ensures the test is not just a tautology.
 *
 * Algorithm version: crash-v1
 *   commitHash = SHA-256( serverSeedHex + "|" + clientSeed + "|" + nonce )
 */
class ProvablyFairServiceTest {

    private ProvablyFairService pf;
    private GameConfig.ProvablyFairConfig pfConfig;

    // ── Fixed golden-vector inputs ────────────────────────────────────────
    // serverSeed: 32 bytes all 0xAB = "abab...ab" (64 hex chars)
    private static final String SERVER_SEED_HEX =
            "ab".repeat(32); // 64 char hex
    private static final String CLIENT_SEED = "player-test";
    private static final long   NONCE        = 42L;

    @BeforeEach
    void setUp() {
        pf = new ProvablyFairService();
        pfConfig = new GameConfig.ProvablyFairConfig();
        // defaults: serverSeedLengthBytes=32, hashAlgorithm="SHA-256"
    }

    // ── algorithmVersion ─────────────────────────────────────────────────

    @Test
    void algorithmVersion_isCrashV1() {
        assertThat(pf.algorithmVersion()).isEqualTo("crash-v1");
    }

    // ── Golden vector: commitHash ─────────────────────────────────────────

    /**
     * Golden vector 1 — verify commitHash matches independent SHA-256 of preimage.
     *
     * Pre-image: "abab...ab|player-test|42"
     * Expected: independently computed via JDK MessageDigest.
     */
    @Test
    void commitHash_goldenVector_matchesIndependentSha256() throws Exception {
        // Independent reference calculation (NOT using the service)
        String preimage = SERVER_SEED_HEX + "|" + CLIENT_SEED + "|" + NONCE;
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] expectedBytes = md.digest(preimage.getBytes(StandardCharsets.UTF_8));
        String expectedHex = HexFormat.of().formatHex(expectedBytes);

        // Service output
        String actual = pf.computeCommitHash(SERVER_SEED_HEX, CLIENT_SEED, NONCE, "SHA-256");

        assertThat(actual)
                .as("commitHash must match SHA-256 of preimage (golden vector 1)")
                .isEqualTo(expectedHex);
    }

    @Test
    void commitHash_is64LowercaseHexChars() {
        String hash = pf.computeCommitHash(SERVER_SEED_HEX, CLIENT_SEED, NONCE, "SHA-256");
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    void commitHash_isDeterministic() {
        String h1 = pf.computeCommitHash(SERVER_SEED_HEX, CLIENT_SEED, NONCE, "SHA-256");
        String h2 = pf.computeCommitHash(SERVER_SEED_HEX, CLIENT_SEED, NONCE, "SHA-256");
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    void commitHash_changesWithDifferentNonce() {
        String h1 = pf.computeCommitHash(SERVER_SEED_HEX, CLIENT_SEED, 1L, "SHA-256");
        String h2 = pf.computeCommitHash(SERVER_SEED_HEX, CLIENT_SEED, 2L, "SHA-256");
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void commitHash_changesWithDifferentSeed() {
        String other = "cd".repeat(32);
        String h1 = pf.computeCommitHash(SERVER_SEED_HEX, CLIENT_SEED, NONCE, "SHA-256");
        String h2 = pf.computeCommitHash(other, CLIENT_SEED, NONCE, "SHA-256");
        assertThat(h1).isNotEqualTo(h2);
    }

    // ── Verify endpoint (S7 preview) ──────────────────────────────────────

    @Test
    void verify_returnsTrueForMatchingCommit() {
        String commit = pf.computeCommitHash(SERVER_SEED_HEX, CLIENT_SEED, NONCE, "SHA-256");
        assertThat(pf.verify(SERVER_SEED_HEX, CLIENT_SEED, NONCE, commit, "SHA-256")).isTrue();
    }

    @Test
    void verify_returnsFalseForWrongSeed() {
        String commit = pf.computeCommitHash(SERVER_SEED_HEX, CLIENT_SEED, NONCE, "SHA-256");
        String wrongSeed = "00".repeat(32);
        assertThat(pf.verify(wrongSeed, CLIENT_SEED, NONCE, commit, "SHA-256")).isFalse();
    }

    @Test
    void verify_caseInsensitiveCommitComparison() {
        String commit = pf.computeCommitHash(SERVER_SEED_HEX, CLIENT_SEED, NONCE, "SHA-256");
        assertThat(pf.verify(SERVER_SEED_HEX, CLIENT_SEED, NONCE, commit.toUpperCase(), "SHA-256"))
                .isTrue();
    }

    // ── generateSecrets ───────────────────────────────────────────────────

    @Test
    void generateSecrets_producesValidCommit() {
        RoundSecrets secrets = pf.generateSecrets("my-seed", 1L, pfConfig);

        assertThat(secrets.serverSeedHex()).matches("[0-9a-f]{64}"); // 32 bytes hex
        assertThat(secrets.commitHash()).matches("[0-9a-f]{64}");
        assertThat(secrets.clientSeed()).isEqualTo("my-seed");
        assertThat(secrets.nonce()).isEqualTo(1L);

        // commitHash must be self-consistent
        assertThat(pf.verify(secrets.serverSeedHex(), secrets.clientSeed(),
                secrets.nonce(), secrets.commitHash(), "SHA-256")).isTrue();
    }

    @Test
    void generateSecrets_differentCallsProduceDifferentSeeds() {
        RoundSecrets s1 = pf.generateSecrets("seed", 1L, pfConfig);
        RoundSecrets s2 = pf.generateSecrets("seed", 1L, pfConfig);
        // Different calls → different server seeds (SecureRandom)
        assertThat(s1.serverSeedHex()).isNotEqualTo(s2.serverSeedHex());
    }

    @Test
    void generateSecrets_usesFixedSeed_whenProfileDevAndConfigured() {
        org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
        env.setActiveProfiles("dev");
        ProvablyFairService withEnv = new ProvablyFairService(env);
        pfConfig.setDevFixedServerSeed(SERVER_SEED_HEX);

        RoundSecrets a = withEnv.generateSecrets(CLIENT_SEED, NONCE, pfConfig);
        RoundSecrets b = withEnv.generateSecrets(CLIENT_SEED, NONCE, pfConfig);

        assertThat(a.serverSeedHex()).isEqualTo(SERVER_SEED_HEX);
        assertThat(b.serverSeedHex()).isEqualTo(SERVER_SEED_HEX);
        assertThat(a.commitHash()).isEqualTo(b.commitHash());
    }

    @Test
    void generateSecrets_rejectsFixedSeed_outsideDevOrTestProfile() {
        org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
        env.setActiveProfiles("prod");
        ProvablyFairService withEnv = new ProvablyFairService(env);
        pfConfig.setDevFixedServerSeed(SERVER_SEED_HEX);

        assertThatThrownBy(() -> withEnv.generateSecrets(CLIENT_SEED, NONCE, pfConfig))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev/test");
    }

    @Test
    void generateSecrets_rejectsFixedSeed_whenNoEnvironment() {
        pfConfig.setDevFixedServerSeed(SERVER_SEED_HEX);
        assertThatThrownBy(() -> pf.generateSecrets(CLIENT_SEED, NONCE, pfConfig))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev/test");
    }
}
