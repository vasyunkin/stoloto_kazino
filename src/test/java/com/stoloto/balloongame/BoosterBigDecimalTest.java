package com.stoloto.balloongame;

import com.stoloto.balloongame.config.GameConfig;
import com.stoloto.balloongame.service.BoosterResult;
import com.stoloto.balloongame.service.ScoringService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S15 / TD-06 — booster multiplier stays on BigDecimal path (no double in bonus math).
 */
class BoosterBigDecimalTest {

    @Test
    void boosterBonus_usesBigDecimalHalfUp_withoutDoubleCast() {
        GameConfig config = new GameConfig();
        config.setPointsPerLine(10);
        // 1.15 × 10 = 11.5 → HALF_UP → 12
        BoosterResult booster = new BoosterResult(
                1, "Precise", new BigDecimal("1.15"), 2);
        assertThat(new ScoringService().boosterBonus(booster, config)).isEqualTo(12);
    }

    @Test
    void boosterTier_multiplierIsBigDecimalField() throws NoSuchFieldException {
        assertThat(GameConfig.BoosterTier.class.getDeclaredField("multiplier").getType())
                .isEqualTo(BigDecimal.class);
        assertThat(BoosterResult.class.getRecordComponents()[2].getType())
                .isEqualTo(BigDecimal.class);
    }
}
