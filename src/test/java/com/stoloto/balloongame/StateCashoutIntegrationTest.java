package com.stoloto.balloongame;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stoloto.balloongame.api.dto.BetRequest;
import com.stoloto.balloongame.api.dto.CashoutResponse;
import com.stoloto.balloongame.api.dto.GameStateResponse;
import com.stoloto.balloongame.api.dto.StartGameResponse;
import com.stoloto.balloongame.api.exception.ErrorCode;
import com.stoloto.balloongame.api.exception.GameException;
import com.stoloto.balloongame.config.GameConfig;
import com.stoloto.balloongame.domain.entity.GameRound;
import com.stoloto.balloongame.domain.entity.LedgerType;
import com.stoloto.balloongame.domain.entity.Player;
import com.stoloto.balloongame.domain.entity.RoundStatus;
import com.stoloto.balloongame.domain.repository.GameRoundRepository;
import com.stoloto.balloongame.domain.repository.PlayerRepository;
import com.stoloto.balloongame.domain.repository.WalletLedgerRepository;
import com.stoloto.balloongame.service.CrashMathService;
import com.stoloto.balloongame.service.GameOrchestrator;
import com.stoloto.balloongame.service.GameSessionCache;
import com.stoloto.balloongame.service.PlayerService;
import com.stoloto.balloongame.service.RoundLifecycleService;
import com.stoloto.balloongame.support.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S5 — state, crash, cashout. Money loop closed under one per-round lock (I4).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class StateCashoutIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-06-01T12:00:00Z");
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
    @Autowired PlayerRepository playerRepository;
    @Autowired GameRoundRepository gameRoundRepository;
    @Autowired WalletLedgerRepository walletLedgerRepository;
    @Autowired GameOrchestrator orchestrator;
    @Autowired GameSessionCache sessionCache;
    @Autowired CrashMathService crashMath;
    @Autowired GameConfig gameConfig;

    @AfterEach
    void resetClock() {
        clock.useSystemUtc();
    }

    private static String uid() {
        return "s5-" + UUID.randomUUID();
    }

    private void fund(String playerId, String amount) {
        playerService.deposit(playerId, new BigDecimal(amount));
    }

    private BetRequest bet(String playerId, String amount) {
        return new BetRequest(playerId, new BigDecimal(amount), "STANDARD", "AUTO", null);
    }

    private StartGameResponse startOk(String playerId, String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/game/start")
                        .header("X-Player-Id", playerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bet(playerId, amount))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), StartGameResponse.class);
    }

    private record FlyingRound(String playerId, UUID gameId, GameRound round) {}

    /** Start until crashPoint is high enough that t=0 is still FLYING. */
    private FlyingRound flyingRound() throws Exception {
        for (int i = 0; i < 60; i++) {
            clock.freeze(T0);
            String playerId = uid();
            fund(playerId, "1000.00");
            StartGameResponse start = startOk(playerId, "100");
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
                .orElse(gameConfig.getMath().getGrowthRate());
    }

    private GameStateResponse getState(String playerId, UUID gameId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/game/state/" + gameId)
                        .header("X-Player-Id", playerId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), GameStateResponse.class);
    }

    // ── I1 ────────────────────────────────────────────────────────────────

    @Test
    void state_flying_doesNotLeakCrashPointOrSeed() throws Exception {
        FlyingRound flying = flyingRound();

        MvcResult result = mockMvc.perform(get("/api/game/state/" + flying.gameId())
                        .header("X-Player-Id", flying.playerId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FLYING"))
                .andExpect(jsonPath("$.multiplier").exists())
                .andExpect(jsonPath("$.crashPoint").doesNotExist())
                .andExpect(jsonPath("$.serverSeed").doesNotExist())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertThat(json)
                .doesNotContain("\"crashPoint\"")
                .doesNotContain("\"serverSeed\"");
    }

    @Test
    void state_afterCrash_revealsSeedAndCrashPoint() throws Exception {
        FlyingRound flying = flyingRound();
        freezePastCrash(flying.round());

        GameStateResponse state = getState(flying.playerId(), flying.gameId());
        assertThat(state.status()).isEqualTo(RoundStatus.CRASHED);
        assertThat(state.zone()).isEqualTo("CRASHED");
        assertThat(state.crashPoint()).isEqualByComparingTo(flying.round().getCrashPoint());
        assertThat(state.serverSeed()).isEqualTo(flying.round().getServerSeed());
        assertThat(state.winAmount()).isNull();
    }

    // ── I6 ────────────────────────────────────────────────────────────────

    @Test
    void state_foreignPlayer_returns403() throws Exception {
        FlyingRound flying = flyingRound();
        mockMvc.perform(get("/api/game/state/" + flying.gameId())
                        .header("X-Player-Id", "intruder"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void cashout_foreignPlayer_returns403() throws Exception {
        FlyingRound flying = flyingRound();
        mockMvc.perform(post("/api/game/cashout/" + flying.gameId())
                        .header("X-Player-Id", "intruder"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(playerService.getBalance(flying.playerId())).isEqualByComparingTo("900.00");
    }

    @Test
    void state_unknownGame_returns404() throws Exception {
        mockMvc.perform(get("/api/game/state/" + UUID.randomUUID())
                        .header("X-Player-Id", uid()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));
    }

    // ── I5 win / loss ─────────────────────────────────────────────────────

    @Test
    void cashout_beforeCrash_creditsWinAndLedger() throws Exception {
        FlyingRound flying = flyingRound();
        freezeJustBelowCrash(flying.round());

        CashoutResponse cashout = orchestrator.cashout(flying.gameId(), flying.playerId());
        assertThat(cashout.serverSeed()).isEqualTo(flying.round().getServerSeed());
        // Effective K may exceed crashPoint when booster activated (crash still uses raw K).
        assertThat(cashout.multiplierAtCashout()).isGreaterThanOrEqualTo(new BigDecimal("1.20"));

        BigDecimal expectedWin = new BigDecimal("100")
                .multiply(cashout.multiplierAtCashout())
                .setScale(4, RoundingMode.HALF_UP);
        assertThat(cashout.winAmount()).isEqualByComparingTo(expectedWin);

        Player player = playerRepository.findByExternalId(flying.playerId()).orElseThrow();
        BigDecimal expectedBalance = new BigDecimal("900.00").add(expectedWin);
        assertThat(playerService.getBalance(flying.playerId())).isEqualByComparingTo(expectedBalance);
        assertThat(player.getBalance())
                .isEqualByComparingTo(walletLedgerRepository.sumBalanceByPlayerId(player.getId()));

        long wins = walletLedgerRepository.findAllByPlayerIdOrderByCreatedAtDesc(player.getId())
                .stream()
                .filter(e -> e.getType() == LedgerType.CREDIT_WIN)
                .count();
        assertThat(wins).isEqualTo(1);

        GameRound persisted = gameRoundRepository.findById(flying.gameId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(RoundStatus.CASHED_OUT);
        assertThat(persisted.getEndedAt()).isNotNull();
        assertThat(persisted.getPointsEarned()).isGreaterThanOrEqualTo(0);
        assertThat(persisted.getServerSeed()).isNotBlank();
    }

    @Test
    void state_detectsCrash_noCredit() throws Exception {
        FlyingRound flying = flyingRound();
        freezePastCrash(flying.round());

        GameStateResponse state = orchestrator.state(flying.gameId(), flying.playerId());
        assertThat(state.status()).isEqualTo(RoundStatus.CRASHED);

        Player player = playerRepository.findByExternalId(flying.playerId()).orElseThrow();
        assertThat(playerService.getBalance(flying.playerId())).isEqualByComparingTo("900.00");
        assertThat(player.getBalance())
                .isEqualByComparingTo(walletLedgerRepository.sumBalanceByPlayerId(player.getId()));
        assertThat(walletLedgerRepository.findAllByPlayerIdOrderByCreatedAtDesc(player.getId()))
                .noneMatch(e -> e.getType() == LedgerType.CREDIT_WIN);

        GameRound persisted = gameRoundRepository.findById(flying.gameId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(RoundStatus.CRASHED);
        assertThat(persisted.getEndedAt()).isNotNull();
        assertThat(persisted.getPointsEarned()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void cashout_afterCrash_returns409() throws Exception {
        FlyingRound flying = flyingRound();
        freezePastCrash(flying.round());
        orchestrator.state(flying.gameId(), flying.playerId());

        mockMvc.perform(post("/api/game/cashout/" + flying.gameId())
                        .header("X-Player-Id", flying.playerId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_CRASHED"));

        assertThat(playerService.getBalance(flying.playerId())).isEqualByComparingTo("900.00");
    }

    @Test
    void cashout_twice_returns409_noDoubleCredit() throws Exception {
        FlyingRound flying = flyingRound();
        freezeJustBelowCrash(flying.round());

        CashoutResponse first = orchestrator.cashout(flying.gameId(), flying.playerId());
        BigDecimal balanceAfterWin = playerService.getBalance(flying.playerId());

        mockMvc.perform(post("/api/game/cashout/" + flying.gameId())
                        .header("X-Player-Id", flying.playerId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_CASHED_OUT"));

        assertThat(playerService.getBalance(flying.playerId())).isEqualByComparingTo(balanceAfterWin);
        Player player = playerRepository.findByExternalId(flying.playerId()).orElseThrow();
        long wins = walletLedgerRepository.findAllByPlayerIdOrderByCreatedAtDesc(player.getId())
                .stream()
                .filter(e -> e.getType() == LedgerType.CREDIT_WIN)
                .count();
        assertThat(wins).isEqualTo(1);
        assertThat(first.winAmount()).isPositive();
    }

    // ── I4 race ───────────────────────────────────────────────────────────

    @Test
    void race_cashoutAndState_justBelowCrash_singleWin() throws Exception {
        FlyingRound flying = flyingRound();
        freezeJustBelowCrash(flying.round());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        List<CashoutResponse> cashouts = new CopyOnWriteArrayList<>();
        List<GameStateResponse> states = new CopyOnWriteArrayList<>();
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        Thread.ofVirtual().start(() -> {
            ready.countDown();
            try {
                go.await();
                cashouts.add(orchestrator.cashout(flying.gameId(), flying.playerId()));
            } catch (Throwable t) {
                errors.add(t);
            } finally {
                done.countDown();
            }
        });
        Thread.ofVirtual().start(() -> {
            ready.countDown();
            try {
                go.await();
                states.add(orchestrator.state(flying.gameId(), flying.playerId()));
            } catch (Throwable t) {
                errors.add(t);
            } finally {
                done.countDown();
            }
        });

        ready.await();
        go.countDown();
        done.await(30, TimeUnit.SECONDS);

        GameRound persisted = gameRoundRepository.findById(flying.gameId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(RoundStatus.CASHED_OUT);
        assertThat(cashouts).hasSize(1);
        assertThat(states).hasSize(1);
        assertThat(errors).allMatch(t -> t instanceof GameException ge
                && ge.getErrorCode() == ErrorCode.ALREADY_CASHED_OUT);

        Player player = playerRepository.findByExternalId(flying.playerId()).orElseThrow();
        assertThat(playerService.getBalance(flying.playerId()))
                .isEqualByComparingTo(walletLedgerRepository.sumBalanceByPlayerId(player.getId()));
        long wins = walletLedgerRepository.findAllByPlayerIdOrderByCreatedAtDesc(player.getId())
                .stream()
                .filter(e -> e.getType() == LedgerType.CREDIT_WIN)
                .count();
        assertThat(wins).isEqualTo(1);
    }

    @Test
    void race_cashoutAndState_pastCrash_singleCrashNoWin() throws Exception {
        FlyingRound flying = flyingRound();
        freezePastCrash(flying.round());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        List<GameStateResponse> states = new CopyOnWriteArrayList<>();
        List<Throwable> cashoutErrors = new CopyOnWriteArrayList<>();

        Thread.ofVirtual().start(() -> {
            ready.countDown();
            try {
                go.await();
                orchestrator.cashout(flying.gameId(), flying.playerId());
            } catch (Throwable t) {
                cashoutErrors.add(t);
            } finally {
                done.countDown();
            }
        });
        Thread.ofVirtual().start(() -> {
            ready.countDown();
            try {
                go.await();
                states.add(orchestrator.state(flying.gameId(), flying.playerId()));
            } catch (Throwable t) {
                cashoutErrors.add(t);
            } finally {
                done.countDown();
            }
        });

        ready.await();
        go.countDown();
        done.await(30, TimeUnit.SECONDS);

        GameRound persisted = gameRoundRepository.findById(flying.gameId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(RoundStatus.CRASHED);
        assertThat(states).isNotEmpty();
        assertThat(states.getFirst().status()).isEqualTo(RoundStatus.CRASHED);
        assertThat(cashoutErrors).anyMatch(t -> t instanceof GameException ge
                && ge.getErrorCode() == ErrorCode.ALREADY_CRASHED);

        Player player = playerRepository.findByExternalId(flying.playerId()).orElseThrow();
        assertThat(playerService.getBalance(flying.playerId())).isEqualByComparingTo("900.00");
        assertThat(player.getBalance())
                .isEqualByComparingTo(walletLedgerRepository.sumBalanceByPlayerId(player.getId()));
        assertThat(walletLedgerRepository.findAllByPlayerIdOrderByCreatedAtDesc(player.getId()))
                .noneMatch(e -> e.getType() == LedgerType.CREDIT_WIN);
    }

    // ── I8 ────────────────────────────────────────────────────────────────

    @Test
    void state_usesConfigSnapshot_notLiveBean() throws Exception {
        FlyingRound flying = null;
        for (int i = 0; i < 40; i++) {
            clock.freeze(T0);
            String playerId = uid();
            fund(playerId, "1000.00");
            BetRequest body = new BetRequest(playerId, new BigDecimal("100"), "STANDARD", "NONE", null);
            MvcResult startResult = mockMvc.perform(post("/api/game/start")
                            .header("X-Player-Id", playerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andReturn();
            StartGameResponse start = objectMapper.readValue(
                    startResult.getResponse().getContentAsString(), StartGameResponse.class);
            GameRound round = gameRoundRepository.findById(start.gameId()).orElseThrow();
            if (round.getCrashPoint().compareTo(new BigDecimal("1.3500")) >= 0) {
                flying = new FlyingRound(playerId, start.gameId(), round);
                break;
            }
        }
        assertThat(flying).isNotNull();

        double snapshotRate = sessionCache.get(flying.gameId()).orElseThrow()
                .getConfigSnapshot().getMath().getGrowthRate();
        double original = gameConfig.getMath().getGrowthRate();
        try {
            gameConfig.getMath().setGrowthRate(0.5);
            freezeAtMultiplier(flying.round(), new BigDecimal("1.2500"));

            GameStateResponse state = orchestrator.state(flying.gameId(), flying.playerId());
            BigDecimal expected = crashMath.multiplierAt(
                    flying.round().getStartedAt(), clock.instant(), snapshotRate);
            assertThat(state.status()).isEqualTo(RoundStatus.FLYING);
            assertThat(state.multiplier()).isEqualByComparingTo(expected);
            BigDecimal ifLiveUsed = crashMath.multiplierAt(
                    flying.round().getStartedAt(), clock.instant(), 0.5);
            assertThat(state.multiplier()).isNotEqualByComparingTo(ifLiveUsed);
        } finally {
            gameConfig.getMath().setGrowthRate(original);
        }
    }

    // ── cache miss rebuild ────────────────────────────────────────────────

    @Test
    void state_flyingCacheMiss_returns410() throws Exception {
        FlyingRound flying = flyingRound();
        sessionCache.evict(flying.gameId());

        mockMvc.perform(get("/api/game/state/" + flying.gameId())
                        .header("X-Player-Id", flying.playerId()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("ROUND_EXPIRED"));
    }

    // ── I7 ────────────────────────────────────────────────────────────────

    @Test
    void crash_doesNotLogServerSeed() throws Exception {
        FlyingRound flying = flyingRound();
        freezePastCrash(flying.round());

        Logger logger = (Logger) LoggerFactory.getLogger(RoundLifecycleService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            orchestrator.state(flying.gameId(), flying.playerId());
            String seed = flying.round().getServerSeed();
            assertThat(appender.list)
                    .noneMatch(e -> e.getFormattedMessage() != null
                            && e.getFormattedMessage().contains(seed));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
