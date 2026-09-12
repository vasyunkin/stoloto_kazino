package com.stoloto.balloongame;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stoloto.balloongame.api.exception.ErrorCode;
import com.stoloto.balloongame.api.exception.GameException;
import com.stoloto.balloongame.config.GameConfig;
import com.stoloto.balloongame.domain.repository.ConfigSnapshotRepository;
import com.stoloto.balloongame.service.ConfigService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * S4/S8 — ConfigService snapshots and partial merge (I8).
 */
class ConfigServiceTest {

    private GameConfig live;
    private ConfigService configService;
    private ConfigSnapshotRepository snapshotRepository;

    @BeforeEach
    void setUp() {
        live = new GameConfig();
        live.getMath().setGrowthRate(0.065);
        live.getMath().setHouseEdge(0.04);
        live.getAdmin().setMinBetAmount(new BigDecimal("10"));
        live.getBoosters().setTiers(List.of(tier(1, "Flame", 1.5, 50)));
        snapshotRepository = mock(ConfigSnapshotRepository.class);
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        configService = new ConfigService(live, new ObjectMapper(), validator, snapshotRepository);
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
        configService = new ConfigService(live, new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator(), snapshotRepository);

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

    @Test
    void mergeAndApply_patchesGrowthRate_keepsHouseEdge() {
        configService.mergeAndApply("{\"math\":{\"growthRate\":0.2}}", "test");

        GameConfig snapshot = configService.getSnapshot();
        assertThat(snapshot.getMath().getGrowthRate()).isEqualTo(0.2);
        assertThat(snapshot.getMath().getHouseEdge()).isEqualTo(0.04);
        verify(snapshotRepository).save(any());
    }

    @Test
    void mergeAndApply_invalidHouseEdge_throws() {
        assertThatThrownBy(() -> configService.mergeAndApply("{\"math\":{\"houseEdge\":0.9}}", "test"))
                .isInstanceOf(GameException.class)
                .extracting(ex -> ((GameException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFIG_VALIDATION_FAILED);
    }

    private static GameConfig.BoosterTier tier(int n, String name, double mult, int w) {
        GameConfig.BoosterTier t = new GameConfig.BoosterTier();
        t.setTier(n);
        t.setName(name);
        t.setMultiplier(java.math.BigDecimal.valueOf(mult));
        t.setProbabilityWeight(w);
        return t;
    }
}
