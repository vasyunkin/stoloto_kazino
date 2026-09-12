package com.stoloto.balloongame;

import com.stoloto.balloongame.domain.entity.RoundStatus;
import com.stoloto.balloongame.service.PuzzleService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S12 — puzzle piece formula (must depend on outcome, not seed alone).
 */
class PuzzleServiceTest {

    private final PuzzleService puzzle = new PuzzleService();

    private static final String SEED = "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899";

    @Test
    void sameSeed_cashoutVsCrash_differentPiece() {
        OptionalInt cashout = puzzle.roll(SEED, RoundStatus.CASHED_OUT, new BigDecimal("1.5000"));
        OptionalInt crash = puzzle.roll(SEED, RoundStatus.CRASHED, null);

        assertThat(cashout).isPresent();
        assertThat(crash).isPresent();
        assertThat(cashout.getAsInt())
                .as("cashout and crash must not share a piece for the same seed")
                .isNotEqualTo(crash.getAsInt());
        assertThat(cashout.getAsInt()).isBetween(0, PuzzleService.PIECE_COUNT - 1);
        assertThat(crash.getAsInt()).isBetween(0, PuzzleService.PIECE_COUNT - 1);
    }

    @Test
    void sameSeedStatusOutcome_isStable() {
        int a = puzzle.roll(SEED, RoundStatus.CASHED_OUT, new BigDecimal("1.5000")).orElseThrow();
        int b = puzzle.roll(SEED, RoundStatus.CASHED_OUT, new BigDecimal("1.5")).orElseThrow();
        assertThat(a).isEqualTo(b);

        int c = puzzle.roll(SEED, RoundStatus.CRASHED, null).orElseThrow();
        int d = puzzle.roll(SEED, RoundStatus.CRASHED, new BigDecimal("99")).orElseThrow();
        assertThat(c).isEqualTo(d);
    }

    @Test
    void differentCashoutMultiplier_canDiffer() {
        int low = puzzle.roll(SEED, RoundStatus.CASHED_OUT, new BigDecimal("1.1000")).orElseThrow();
        int high = puzzle.roll(SEED, RoundStatus.CASHED_OUT, new BigDecimal("2.5000")).orElseThrow();
        assertThat(low).isNotEqualTo(high);
    }

    @Test
    void voidAndFlying_noPiece() {
        assertThat(puzzle.roll(SEED, RoundStatus.VOID, null)).isEmpty();
        assertThat(puzzle.roll(SEED, RoundStatus.FLYING, null)).isEmpty();
    }

    @Test
    void golden_cashout150_matchesFormula() {
        String material = SEED + ":CASHED_OUT:1.5000";
        int expected = PuzzleService.indexFromMaterial(material);
        assertThat(puzzle.roll(SEED, RoundStatus.CASHED_OUT, new BigDecimal("1.5000")))
                .hasValue(expected);
    }

    @Test
    void cashoutWithoutMultiplier_throws() {
        assertThatThrownBy(() -> PuzzleService.outcomeKey(RoundStatus.CASHED_OUT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
