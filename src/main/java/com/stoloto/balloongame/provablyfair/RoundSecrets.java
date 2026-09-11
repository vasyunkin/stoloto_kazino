package com.stoloto.balloongame.provablyfair;

/**
 * Immutable cryptographic secrets for one game round.
 *
 * <ul>
 *   <li>{@code serverSeedHex} — lowercase hex of 32 random bytes; stored in {@code game_round.server_seed}.
 *       Never sent to client while FLYING (I1, I7).</li>
 *   <li>{@code clientSeed}    — optional player-supplied entropy (max 64 chars).</li>
 *   <li>{@code nonce}         — strictly increasing per-player counter (or global sequence).</li>
 *   <li>{@code commitHash}    — SHA-256 of {@code serverSeedHex + "|" + clientSeed + "|" + nonce}.
 *       Sent to client in {@code StartGameResponse}; verifiable post-round.</li>
 * </ul>
 *
 * Algorithm version: {@code crash-v1} (see ProvablyFairService and §7 of the spec).
 */
public record RoundSecrets(
        String serverSeedHex,
        String clientSeed,
        long   nonce,
        String commitHash
) {}
