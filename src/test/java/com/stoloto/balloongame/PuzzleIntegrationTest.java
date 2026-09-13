package com.stoloto.balloongame;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stoloto.balloongame.api.dto.BetRequest;
import com.stoloto.balloongame.api.dto.CashoutResponse;
import com.stoloto.balloongame.api.dto.GameStateResponse;
import com.stoloto.balloongame.api.dto.StartGameResponse;
import com.stoloto.balloongame.domain.entity.GameRound;
import com.stoloto.balloongame.domain.entity.RoundStatus;
import com.stoloto.balloongame.domain.repository.GameRoundRepository;
import com.stoloto.balloongame.service.CrashMathService;
import com.stoloto.balloongame.service.GameSessionCache;
import com.stoloto.balloongame.service.PlayerService;
import com.stoloto.balloongame.service.PuzzleService;
import com.stoloto.balloongame.service.RoundRecoveryService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S12 — puzzle piece on terminal CRASHED/CASHED_OUT; never on VOID; no wallet credit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class PuzzleIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-12T14:00:00Z");
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
    @Autowired GameSessionCache sessionCache;
    @Autowired CrashMathService crashMath;
    @Autowired PuzzleService puzzleService;
    @Autowired RoundRecoveryService recoveryService;

    @AfterEach
    void resetClock() {
        clock.useSystemUtc();
    }

    private static String uid() {
        return "s12-" + UUID.randomUUID();
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
                .max(new BigDecimal("1.0100"));
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

    @Test
    void cashout_awardsPuzzle_andPersists() throws Exception {
        FlyingRound flying = flyingRound();
        freezeJustBelowCrash(flying.round());

        MvcResult result = mockMvc.perform(post("/api/game/cashout/" + flying.gameId())
                        .header("X-Player-Id", flying.playerId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puzzlePieceIndex").exists())
                .andReturn();

        CashoutResponse cashout = objectMapper.readValue(
                result.getResponse().getContentAsString(), CashoutResponse.class);
        assertThat(cashout.puzzlePieceIndex()).isNotNull()
                .isBetween(0, PuzzleService.PIECE_COUNT - 1);

        GameRound persisted = gameRoundRepository.findById(flying.gameId()).orElseThrow();
        assertThat(persisted.getPuzzlePieceIndex()).isEqualTo(cashout.puzzlePieceIndex());

        int expected = puzzleService.roll(
                persisted.getServerSeed(),
                RoundStatus.CASHED_OUT,
                persisted.getCashoutMultiplier()).orElseThrow();
        assertThat(cashout.puzzlePieceIndex()).isEqualTo(expected);
    }

    @Test
    void crash_awardsPuzzle() throws Exception {
        FlyingRound flying = flyingRound();
        freezePastCrash(flying.round());

        MvcResult result = mockMvc.perform(get("/api/game/state/" + flying.gameId())
                        .header("X-Player-Id", flying.playerId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CRASHED"))
                .andExpect(jsonPath("$.puzzlePieceIndex").exists())
                .andReturn();

        GameStateResponse state = objectMapper.readValue(
                result.getResponse().getContentAsString(), GameStateResponse.class);
        GameRound persisted = gameRoundRepository.findById(flying.gameId()).orElseThrow();
        assertThat(state.puzzlePieceIndex()).isEqualTo(persisted.getPuzzlePieceIndex());

        int expected = puzzleService.roll(persisted.getServerSeed(), RoundStatus.CRASHED, null)
                .orElseThrow();
        assertThat(state.puzzlePieceIndex()).isEqualTo(expected);
    }

    @Test
    void void_doesNotAwardPuzzle() throws Exception {
        FlyingRound flying = flyingRound();
        recoveryService.recoverAllFlying();

        GameRound persisted = gameRoundRepository.findById(flying.gameId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(RoundStatus.VOID);
        assertThat(persisted.getPuzzlePieceIndex()).isNull();
    }

    @Test
    void flying_doesNotExposePuzzle() throws Exception {
        FlyingRound flying = flyingRound();
        mockMvc.perform(get("/api/game/state/" + flying.gameId())
                        .header("X-Player-Id", flying.playerId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FLYING"))
                .andExpect(jsonPath("$.puzzlePieceIndex").doesNotExist());
    }

    @Test
    void pollState_samePiece_idempotent() throws Exception {
        FlyingRound flying = flyingRound();
        freezeJustBelowCrash(flying.round());

        CashoutResponse cashout = objectMapper.readValue(
                mockMvc.perform(post("/api/game/cashout/" + flying.gameId())
                                .header("X-Player-Id", flying.playerId()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                CashoutResponse.class);

        for (int i = 0; i < 5; i++) {
            GameStateResponse state = objectMapper.readValue(
                    mockMvc.perform(get("/api/game/state/" + flying.gameId())
                                    .header("X-Player-Id", flying.playerId()))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString(),
                    GameStateResponse.class);
            assertThat(state.puzzlePieceIndex()).isEqualTo(cashout.puzzlePieceIndex());
        }

        GameRound persisted = gameRoundRepository.findById(flying.gameId()).orElseThrow();
        assertThat(persisted.getPuzzlePieceIndex()).isEqualTo(cashout.puzzlePieceIndex());
    }

    @Test
    void puzzle_doesNotChangeBalanceBeyondWin() throws Exception {
        FlyingRound flying = flyingRound();
        BigDecimal beforeCashout = playerService.getBalance(flying.playerId());
        freezeJustBelowCrash(flying.round());

        CashoutResponse cashout = objectMapper.readValue(
                mockMvc.perform(post("/api/game/cashout/" + flying.gameId())
                                .header("X-Player-Id", flying.playerId()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                CashoutResponse.class);

        BigDecimal after = playerService.getBalance(flying.playerId());
        assertThat(after).isEqualByComparingTo(beforeCashout.add(cashout.winAmount()));
        assertThat(cashout.puzzlePieceIndex()).isNotNull();
    }

    @Test
    void sameSeed_cashoutAndCrash_wouldDiffer_inFormula() {
        // Guardrail: integration relies on unit formula; seed from a finished round still differs by outcome.
        String seed = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";
        int cashout = puzzleService.roll(seed, RoundStatus.CASHED_OUT, new BigDecimal("1.5000"))
                .orElseThrow();
        int crash = puzzleService.roll(seed, RoundStatus.CRASHED, null).orElseThrow();
        assertThat(cashout).isNotEqualTo(crash);
    }
}
