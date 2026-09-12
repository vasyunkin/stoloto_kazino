package com.stoloto.balloongame;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stoloto.balloongame.api.dto.BetRequest;
import com.stoloto.balloongame.api.dto.HistoryItemResponse;
import com.stoloto.balloongame.api.dto.StartGameResponse;
import com.stoloto.balloongame.domain.entity.GameRound;
import com.stoloto.balloongame.domain.entity.RoundStatus;
import com.stoloto.balloongame.domain.repository.GameRoundRepository;
import com.stoloto.balloongame.domain.repository.WalletLedgerRepository;
import com.stoloto.balloongame.service.CrashMathService;
import com.stoloto.balloongame.service.GameSessionCache;
import com.stoloto.balloongame.service.PlayerService;
import com.stoloto.balloongame.support.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S11 — public terminal history. Variant A: no player id, no secrets, no player JOIN.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class HistoryIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-12T10:00:00Z");
    private static final BigDecimal MIN_FLYING_CRASH = new BigDecimal("1.3500");

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired MutableClock clock;
    @Autowired PlayerService playerService;
    @Autowired GameRoundRepository gameRoundRepository;
    @Autowired WalletLedgerRepository walletLedgerRepository;
    @Autowired GameSessionCache sessionCache;
    @Autowired CrashMathService crashMath;

    @AfterEach
    void resetClock() {
        clock.useSystemUtc();
    }

    private static String uid() {
        return "s11-" + UUID.randomUUID();
    }

    private void wipeRounds() {
        walletLedgerRepository.deleteAll();
        gameRoundRepository.deleteAll();
    }

    private StartGameResponse startOk(String playerId) throws Exception {
        BetRequest body = new BetRequest(playerId, new BigDecimal("100"), "STANDARD", "AUTO", null);
        MvcResult result = mockMvc.perform(post("/api/game/start")
                        .header("X-Player-Id", playerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), StartGameResponse.class);
    }

    private record FlyingRound(String playerId, UUID gameId, GameRound round) {}

    private FlyingRound flyingRound() throws Exception {
        for (int i = 0; i < 60; i++) {
            clock.freeze(T0);
            String playerId = uid();
            playerService.deposit(playerId, new BigDecimal("1000.00"));
            StartGameResponse start = startOk(playerId);
            GameRound round = gameRoundRepository.findById(start.gameId()).orElseThrow();
            if (round.getCrashPoint().compareTo(MIN_FLYING_CRASH) >= 0) {
                return new FlyingRound(playerId, start.gameId(), round);
            }
        }
        throw new AssertionError("Could not draw crashPoint >= " + MIN_FLYING_CRASH);
    }

    private void freezeJustBelowCrash(GameRound round) {
        BigDecimal target = round.getCrashPoint()
                .subtract(new BigDecimal("0.0200"))
                .max(new BigDecimal("1.2000"));
        freezeAtMultiplier(round, target);
        if (kNow(round).compareTo(round.getCrashPoint()) >= 0) {
            clock.freeze(round.getStartedAt());
        }
    }

    private void freezePastCrash(GameRound round) {
        double r = snapshotRate(round);
        double tCrash = crashMath.timeAtMultiplier(round.getCrashPoint().doubleValue(), r);
        long millis = Math.max(1L, (long) Math.ceil(tCrash * 1000.0) + 25L);
        clock.freeze(round.getStartedAt().plusMillis(millis));
        int guard = 0;
        while (kNow(round).compareTo(round.getCrashPoint()) < 0 && guard++ < 30) {
            clock.freeze(clock.instant().plusMillis(50));
        }
    }

    private BigDecimal kNow(GameRound round) {
        return crashMath.multiplierAt(round.getStartedAt(), clock.instant(), snapshotRate(round));
    }

    private void freezeAtMultiplier(GameRound round, BigDecimal targetK) {
        double r = snapshotRate(round);
        double t = crashMath.timeAtMultiplier(targetK.doubleValue(), r);
        clock.freeze(round.getStartedAt().plusMillis(Math.max(0L, (long) Math.floor(t * 1000.0))));
    }

    private double snapshotRate(GameRound round) {
        return sessionCache.get(round.getId())
                .map(s -> s.getConfigSnapshot().getMath().getGrowthRate())
                .orElseThrow();
    }

    private List<HistoryItemResponse> getHistory(Integer limit) throws Exception {
        var req = get("/api/game/history");
        if (limit != null) {
            req = get("/api/game/history").param("limit", String.valueOf(limit));
        }
        MvcResult result = mockMvc.perform(req)
                .andExpect(status().isOk())
                .andReturn();
        String json = result.getResponse().getContentAsString();
        assertThat(json)
                .doesNotContain("\"serverSeed\"")
                .doesNotContain("\"crashPoint\"")
                .doesNotContain("\"clientSeed\"")
                .doesNotContain("\"playerId\"")
                .doesNotContain("\"playerExternalId\"");
        return objectMapper.readValue(json, new TypeReference<>() {});
    }

    @Test
    void H1_emptyDb_returnsEmptyArray() throws Exception {
        wipeRounds();
        List<HistoryItemResponse> history = getHistory(null);
        assertThat(history).isEmpty();
    }

    @Test
    void H2_cashout_appearsWithoutSecrets() throws Exception {
        FlyingRound flying = flyingRound();
        freezeJustBelowCrash(flying.round());

        mockMvc.perform(post("/api/game/cashout/" + flying.gameId())
                        .header("X-Player-Id", flying.playerId()))
                .andExpect(status().isOk());

        List<HistoryItemResponse> history = getHistory(100);
        assertThat(history).anySatisfy(item -> {
            assertThat(item.gameId()).isEqualTo(flying.gameId());
            assertThat(item.status()).isEqualTo(RoundStatus.CASHED_OUT);
            assertThat(item.winAmount()).isNotNull();
            assertThat(item.betAmount()).isEqualByComparingTo("100");
        });
    }

    @Test
    void H3_crash_appearsWithoutSecrets() throws Exception {
        FlyingRound flying = flyingRound();
        freezePastCrash(flying.round());

        mockMvc.perform(get("/api/game/state/" + flying.gameId())
                        .header("X-Player-Id", flying.playerId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CRASHED"));

        List<HistoryItemResponse> history = getHistory(100);
        assertThat(history).anySatisfy(item -> {
            assertThat(item.gameId()).isEqualTo(flying.gameId());
            assertThat(item.status()).isEqualTo(RoundStatus.CRASHED);
            assertThat(item.winAmount()).isNull();
        });
    }

    @Test
    void H4_flying_notInHistory() throws Exception {
        wipeRounds();
        FlyingRound flying = flyingRound();

        List<HistoryItemResponse> history = getHistory(100);
        assertThat(history).noneMatch(item -> item.gameId().equals(flying.gameId()));
        assertThat(history).isEmpty();
    }

    @Test
    void H5_void_appearsInHistory() throws Exception {
        FlyingRound flying = flyingRound();
        GameRound round = gameRoundRepository.findById(flying.gameId()).orElseThrow();
        round.setStatus(RoundStatus.VOID);
        round.setEndedAt(T0.plusSeconds(1));
        gameRoundRepository.save(round);

        List<HistoryItemResponse> history = getHistory(100);
        assertThat(history).anySatisfy(item -> {
            assertThat(item.gameId()).isEqualTo(flying.gameId());
            assertThat(item.status()).isEqualTo(RoundStatus.VOID);
        });
    }

    @Test
    void H6_newestFirst_andLimit() throws Exception {
        wipeRounds();

        FlyingRound first = flyingRound();
        freezeJustBelowCrash(first.round());
        mockMvc.perform(post("/api/game/cashout/" + first.gameId())
                        .header("X-Player-Id", first.playerId()))
                .andExpect(status().isOk());
        GameRound firstPersisted = gameRoundRepository.findById(first.gameId()).orElseThrow();
        firstPersisted.setEndedAt(T0.plusSeconds(1));
        gameRoundRepository.save(firstPersisted);

        FlyingRound second = flyingRound();
        freezeJustBelowCrash(second.round());
        mockMvc.perform(post("/api/game/cashout/" + second.gameId())
                        .header("X-Player-Id", second.playerId()))
                .andExpect(status().isOk());
        GameRound secondPersisted = gameRoundRepository.findById(second.gameId()).orElseThrow();
        secondPersisted.setEndedAt(T0.plusSeconds(10));
        gameRoundRepository.save(secondPersisted);

        List<HistoryItemResponse> limited = getHistory(1);
        assertThat(limited).hasSize(1);
        assertThat(limited.get(0).gameId()).isEqualTo(second.gameId());

        List<HistoryItemResponse> both = getHistory(10);
        assertThat(both).hasSizeGreaterThanOrEqualTo(2);
        assertThat(both.get(0).gameId()).isEqualTo(second.gameId());
        assertThat(both.get(1).gameId()).isEqualTo(first.gameId());
    }

    @Test
    void H7_I1_flyingStateStillHidesSecrets() throws Exception {
        FlyingRound flying = flyingRound();
        mockMvc.perform(get("/api/game/state/" + flying.gameId())
                        .header("X-Player-Id", flying.playerId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FLYING"))
                .andExpect(jsonPath("$.crashPoint").doesNotExist())
                .andExpect(jsonPath("$.serverSeed").doesNotExist());
    }

    @Test
    void history_doesNotRequirePlayerHeader() throws Exception {
        mockMvc.perform(get("/api/game/history"))
                .andExpect(status().isOk());
    }
}
