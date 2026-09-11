package com.stoloto.balloongame;

import com.stoloto.balloongame.config.GameConfig;
import com.stoloto.balloongame.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S4 — ConfigService returns independent deep copies (I8).
 */
class ConfigServiceTest {

    private GameConfig live;
    private ConfigService configService;

    @BeforeEach
    void setUp() {
        live = new GameConfig();
        live.getMath().setGrowthRate(0.065);
        live.getAdmin().setMinBetAmount(new java.math.BigDecimal("10"));
        configService = new ConfigService(live);
    }

    @Test
    void getSnapshot_isIndependentOfLiveBean() {
        GameConfig snapshot = configService.getSnapshot();
        snapshot.getMath().setGrowthRate(0.999);

        assertThat(live.getMath().getGrowthRate()).isEqualTo(0.065);
        assertThat(configService.getSnapshot().getMath().getGrowthRate()).isEqualTo(0.065);
    }

    @Test
    void getSnapshot_copiesScoringRedZoneMultiplier() {
        live.getScoring().setRedZoneMultiplier(2.5);
        configService = new ConfigService(live);

        GameConfig snapshot = configService.getSnapshot();
        snapshot.getScoring().setRedZoneMultiplier(9.0);
        assertThat(live.getScoring().getRedZoneMultiplier()).isEqualTo(2.5);
        assertThat(configService.getSnapshot().getScoring().getRedZoneMultiplier()).isEqualTo(2.5);
    }

    @Test
    void getSnapshot_twoCalls_areIndependentCopies() {
        GameConfig a = configService.getSnapshot();
        GameConfig b = configService.getSnapshot();

        assertThat(a).isNotSameAs(b);
        a.getMath().setGrowthRate(0.5);
        assertThat(b.getMath().getGrowthRate()).isEqualTo(0.065);
    }
}
