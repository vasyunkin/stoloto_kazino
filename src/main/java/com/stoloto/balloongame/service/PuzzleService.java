package com.stoloto.balloongame.service;

import com.stoloto.balloongame.domain.entity.RoundStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.OptionalInt;

/**
 * Deterministic puzzle piece for a finished round (S12 / Spec 2).
 *
 * <p>Must include outcome — {@code hash(serverSeed)} alone would give the same piece
 * for cashout and crash of the same round.
 *
 * <pre>
 * material = serverSeed + ":" + status + ":" + outcomeKey
 * outcomeKey = CASHED_OUT → cashoutMultiplier scale 4 HALF_UP
 *              CRASHED    → "CRASH"
 * pieceIndex = unsigned(SHA-256(material)[0..3]) % PIECE_COUNT
 * </pre>
 */
@Service
public class PuzzleService {

    /** Number of distinct puzzle fragments (hackathon board size). */
    public static final int PIECE_COUNT = 24;

    private static final int MONEY_SCALE = 4;

    /**
     * @return empty for FLYING / VOID; otherwise index in {@code [0, PIECE_COUNT)}
     */
    public OptionalInt roll(String serverSeed, RoundStatus status, BigDecimal cashoutMultiplier) {
        if (serverSeed == null || status == null) {
            return OptionalInt.empty();
        }
        if (status == RoundStatus.FLYING || status == RoundStatus.VOID) {
            return OptionalInt.empty();
        }
        String outcomeKey = outcomeKey(status, cashoutMultiplier);
        String material = serverSeed + ":" + status.name() + ":" + outcomeKey;
        return OptionalInt.of(indexFromMaterial(material));
    }

    public static String outcomeKey(RoundStatus status, BigDecimal cashoutMultiplier) {
        return switch (status) {
            case CASHED_OUT -> {
                if (cashoutMultiplier == null) {
                    throw new IllegalArgumentException("cashoutMultiplier required for CASHED_OUT puzzle");
                }
                yield cashoutMultiplier.setScale(MONEY_SCALE, RoundingMode.HALF_UP).toPlainString();
            }
            case CRASHED -> "CRASH";
            case FLYING, VOID -> throw new IllegalArgumentException("No puzzle roll for " + status);
        };
    }

    public static int indexFromMaterial(String material) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(material.getBytes(StandardCharsets.UTF_8));
            int unsigned = ((digest[0] & 0xff) << 24)
                    | ((digest[1] & 0xff) << 16)
                    | ((digest[2] & 0xff) << 8)
                    | (digest[3] & 0xff);
            // Math.floorMod keeps result in [0, PIECE_COUNT) for negative ints.
            return Math.floorMod(unsigned, PIECE_COUNT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
