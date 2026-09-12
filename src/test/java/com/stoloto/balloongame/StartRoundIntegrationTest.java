package com.stoloto.balloongame;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stoloto.balloongame.api.dto.BetRequest;
import com.stoloto.balloongame.api.dto.StartGameResponse;
import com.stoloto.balloongame.config.GameConfig;
import com.stoloto.balloongame.domain.entity.GameRound;
import com.stoloto.balloongame.domain.entity.LedgerType;
import com.stoloto.balloongame.domain.entity.Player;
import com.stoloto.balloongame.domain.entity.RoundStatus;
import com.stoloto.balloongame.domain.repository.GameRoundRepository;
import com.stoloto.balloongame.domain.repository.PlayerRepository;
import com.stoloto.balloongame.domain.repository.WalletLedgerRepository;
import com.stoloto.balloongame.provablyfair.ProvablyFairService;
import com.stoloto.balloongame.service.GameOrchestrator;
import com.stoloto.balloongame.service.GameSession;
import com.stoloto.balloongame.service.GameSessionCache;
import com.stoloto.balloongame.service.PlayerService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S4 integration tests — {@code POST /api/game/start}.
 *
 * Covers I1 (no secret leak in API), I2 (seed+crash stored at start),
 * I5 (debit + ledger), I6 (header mismatch → 403), I7 (no seed in logs),
 * I8 (config snapshot isolated from live bean).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class StartRoundIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PlayerService playerService;
    @Autowired PlayerRepository playerRepository;
    @Autowired GameRoundRepository gameRoundRepository;
    @Autowired WalletLedgerRepository walletLedgerRepository;
    @Autowired GameSessionCache sessionCache;
    @Autowired GameOrchestrator orchestrator;
    @Autowired ProvablyFairService provablyFairService;
    @Autowired GameConfig gameConfig;

    private static String uid() {
        return "s4-" + UUID.randomUUID();
    }

    private void fund(String playerId, String amount) {
        playerService.deposit(playerId, new BigDecimal(amount));
    }

    private BetRequest bet(String playerId, String amount) {
        return new BetRequest(playerId, new BigDecimal(amount), "STANDARD", "AUTO", null);
    }

    private MvcResult postStart(String headerPlayerId, BetRequest body) throws Exception {
        return mockMvc.perform(post("/api/game/start")
                        .header("X-Player-Id", headerPlayerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
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

    // ── I1 ────────────────────────────────────────────────────────────────

    @Test
    void start_responseDoesNotContainCrashPointOrSeed() throws Exception {
        String playerId = uid();
        fund(playerId, "1000.00");

        MvcResult result = mockMvc.perform(post("/api/game/start")
                        .header("X-Player-Id", playerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bet(playerId, "100"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").exists())
                .andExpect(jsonPath("$.commitHash").exists())
                .andExpect(jsonPath("$.serverSeedHash").exists())
                .andExpect(jsonPath("$.startedAt").exists())
                .andExpect(jsonPath("$.crashPoint").doesNotExist())
                .andExpect(jsonPath("$.serverSeed").doesNotExist())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertThat(json)
                .doesNotContain("\"crashPoint\"")
                .doesNotContain("\"crash_point\"")
                .doesNotContain("\"serverSeed\"")
                .doesNotContain("\"server_seed\"");

        StartGameResponse body = objectMapper.readValue(json, StartGameResponse.class);
        assertThat(body.commitHash()).isEqualTo(body.serverSeedHash());
        assertThat(body.commitHash()).isNotBlank();
    }

    // ── I5 ────────────────────────────────────────────────────────────────

    @Test
    void start_debitsBalanceAndCreatesLedgerEntry() throws Exception {
        String playerId = uid();
        fund(playerId, "1000.00");

        StartGameResponse resp = startOk(playerId, "100");

        Player player = playerRepository.findByExternalId(playerId).orElseThrow();
        assertThat(playerService.getBalance(playerId)).isEqualByComparingTo("900.00");
        assertThat(player.getBalance())
                .as("player.balance must equal SUM(ledger) — I5")
                .isEqualByComparingTo(walletLedgerRepository.sumBalanceByPlayerId(player.getId()));

        var ledger = walletLedgerRepository.findAllByPlayerIdOrderByCreatedAtDesc(player.getId());
        assertThat(ledger).anySatisfy(entry -> {
            assertThat(entry.getType()).isEqualTo(LedgerType.DEBIT_BET);
            assertThat(entry.getAmount()).isEqualByComparingTo("100");
        });
        assertThat(gameRoundRepository.findById(resp.gameId())).isPresent();
    }

    @Test
    void start_insufficientBalance_returns402_noLedgerAndNoRound() throws Exception {
        String playerId = uid();
        playerService.getOrCreate(playerId);

        MvcResult first = postStart(playerId, bet(playerId, "100"));
        assertThat(first.getResponse().getStatus()).isEqualTo(402);
        assertThat(first.getResponse().getContentAsString()).contains("INSUFFICIENT_BALANCE");

        MvcResult second = postStart(playerId, bet(playerId, "100"));
        assertThat(second.getResponse().getStatus()).isEqualTo(402);

        Player player = playerRepository.findByExternalId(playerId).orElseThrow();
        assertThat(walletLedgerRepository.countByPlayerId(player.getId())).isZero();
        assertThat(player.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(gameRoundRepository.countByPlayer_ExternalId(playerId)).isZero();
    }

    @Test
    void start_insufficientBalance_afterDeposit_doesNotAddExtraLedger() throws Exception {
        String playerId = uid();
        fund(playerId, "50.00");

        MvcResult result = postStart(playerId, bet(playerId, "100"));
        assertThat(result.getResponse().getStatus()).isEqualTo(402);

        Player player = playerRepository.findByExternalId(playerId).orElseThrow();
        assertThat(walletLedgerRepository.countByPlayerId(player.getId())).isEqualTo(1);
        assertThat(playerService.getBalance(playerId)).isEqualByComparingTo("50.00");
    }

    // ── I2 ────────────────────────────────────────────────────────────────

    @Test
    void start_dbContainsFlyingRoundWithSeedAndCrashPoint() throws Exception {
        String playerId = uid();
        fund(playerId, "1000.00");

        StartGameResponse resp = startOk(playerId, "100");
        GameRound round = gameRoundRepository.findById(resp.gameId()).orElseThrow();

        assertThat(round.getStatus()).isEqualTo(RoundStatus.FLYING);
        assertThat(round.getServerSeed()).isNotBlank();
        assertThat(round.getCrashPoint()).isGreaterThanOrEqualTo(new BigDecimal("1.0000"));
        assertThat(round.getServerSeedHash()).isEqualTo(resp.commitHash());
        assertThat(round.getEndedAt()).isNull();
        assertThat(round.getGameConfigSnapshot()).isNotBlank();
        assertThat(round.getBetAmount()).isEqualByComparingTo("100");
        assertThat(round.getBalloonType()).isEqualTo("STANDARD");
    }

    @Test
    void start_commitHashMatchesPersistedServerSeedHash() throws Exception {
        String playerId = uid();
        fund(playerId, "1000.00");

        StartGameResponse resp = startOk(playerId, "100");
        GameRound round = gameRoundRepository.findById(resp.gameId()).orElseThrow();

        assertThat(resp.commitHash()).isEqualTo(round.getServerSeedHash());
        assertThat(provablyFairService.verify(
                round.getServerSeed(),
                round.getClientSeed(),
                round.getNonce(),
                round.getServerSeedHash(),
                "SHA-256")).isTrue();
    }

    // ── I6 (start path) ───────────────────────────────────────────────────

    @Test
    void start_xPlayerIdMismatch_returns403() throws Exception {
        String playerId = uid();
        fund(playerId, "1000.00");

        MvcResult result = postStart("someone-else", bet(playerId, "100"));
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(result.getResponse().getContentAsString()).contains("FORBIDDEN");

        Player player = playerRepository.findByExternalId(playerId).orElseThrow();
        assertThat(walletLedgerRepository.countByPlayerId(player.getId())).isEqualTo(1); // deposit only
        assertThat(gameRoundRepository.countByPlayer_ExternalId(playerId)).isZero();
        assertThat(playerService.getBalance(playerId)).isEqualByComparingTo("1000.00");
    }

    // ── Validation ────────────────────────────────────────────────────────

    @Test
    void start_betBelowMin_returns400_INVALID_BET() throws Exception {
        String playerId = uid();
        fund(playerId, "1000.00");

        MvcResult result = postStart(playerId, bet(playerId, "1"));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("INVALID_BET");

        Player player = playerRepository.findByExternalId(playerId).orElseThrow();
        assertThat(walletLedgerRepository.countByPlayerId(player.getId())).isEqualTo(1);
    }

    @Test
    void start_betAboveMax_returns400_INVALID_BET() throws Exception {
        String playerId = uid();
        fund(playerId, "20000.00");

        MvcResult result = postStart(playerId, bet(playerId, "10001"));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("INVALID_BET");
    }

    @Test
    void start_unknownPlayer_returns404() throws Exception {
        String playerId = uid();
        MvcResult result = postStart(playerId, bet(playerId, "100"));
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).contains("PLAYER_NOT_FOUND");
    }

    @Test
    void start_missingXPlayerId_returns400() throws Exception {
        mockMvc.perform(post("/api/game/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bet("p", "100"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void start_clientSeedPersisted_emptyDefault() throws Exception {
        String playerId = uid();
        fund(playerId, "1000.00");

        StartGameResponse withoutSeed = startOk(playerId, "100");
        GameRound round = gameRoundRepository.findById(withoutSeed.gameId()).orElseThrow();
        assertThat(round.getClientSeed()).isEmpty();

        String seededId = uid();
        fund(seededId, "1000.00");
        BetRequest withSeed = new BetRequest(seededId, new BigDecimal("100"), "LUCKY", null, "player-seed-1");
        MvcResult result = mockMvc.perform(post("/api/game/start")
                        .header("X-Player-Id", seededId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withSeed)))
                .andExpect(status().isOk())
                .andReturn();
        StartGameResponse seeded = objectMapper.readValue(
                result.getResponse().getContentAsString(), StartGameResponse.class);
        GameRound seededRound = gameRoundRepository.findById(seeded.gameId()).orElseThrow();
        assertThat(seededRound.getClientSeed()).isEqualTo("player-seed-1");
        assertThat(seededRound.getBalloonType()).isEqualTo("LUCKY");
    }

    // ── I8 ────────────────────────────────────────────────────────────────

    @Test
    void start_configSnapshotIsolatedFromLiveBean() throws Exception {
        String playerId = uid();
        fund(playerId, "1000.00");

        double originalRate = gameConfig.getMath().getGrowthRate();
        StartGameResponse resp = startOk(playerId, "100");
        GameSession session = sessionCache.get(resp.gameId()).orElseThrow();
        double snapshotRate = session.getConfigSnapshot().getMath().getGrowthRate();

        try {
            gameConfig.getMath().setGrowthRate(0.999);
            assertThat(session.getConfigSnapshot().getMath().getGrowthRate())
                    .as("FLYING session must keep the start-time snapshot (I8)")
                    .isEqualTo(snapshotRate);
            assertThat(snapshotRate)
                    .as("STANDARD theme growthRate applied into snapshot")
                    .isEqualTo(gameConfig.getThemes().getStandard().getGrowthRate());
            GameRound round = gameRoundRepository.findById(resp.gameId()).orElseThrow();
            assertThat(round.getGameConfigSnapshot()).contains("growthRate");
        } finally {
            gameConfig.getMath().setGrowthRate(originalRate);
        }
    }

    // ── I7 ────────────────────────────────────────────────────────────────

    @Test
    void start_doesNotLogServerSeed() throws Exception {
        String playerId = uid();
        fund(playerId, "1000.00");

        Logger logger = (Logger) LoggerFactory.getLogger(GameOrchestrator.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            StartGameResponse resp = startOk(playerId, "100");
            GameRound round = gameRoundRepository.findById(resp.gameId()).orElseThrow();
            String seed = round.getServerSeed();
            assertThat(appender.list)
                    .noneMatch(event -> event.getFormattedMessage() != null
                            && event.getFormattedMessage().contains(seed));
        } finally {
            logger.detachAppender(appender);
        }
    }

    // ── Concurrent debit (I5 / Hibernate L1) ──────────────────────────────

    @Test
    void start_concurrentTwoStarts_samePlayer() throws InterruptedException {
        String playerId = uid();
        fund(playerId, "1000.00");

        int threads = 2;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        List<StartGameResponse> ok = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threads; i++) {
            Thread.ofVirtual().start(() -> {
                ready.countDown();
                try {
                    start.await();
                    ok.add(orchestrator.start(bet(playerId, "100"), playerId));
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await(30, TimeUnit.SECONDS);

        assertThat(errors).as("Concurrent starts: %s", errors).isEmpty();
        assertThat(ok).hasSize(2);

        Player player = playerRepository.findByExternalId(playerId).orElseThrow();
        BigDecimal balance = playerService.getBalance(playerId);
        assertThat(balance).isEqualByComparingTo("800.00");
        assertThat(balance).isEqualByComparingTo(walletLedgerRepository.sumBalanceByPlayerId(player.getId()));

        long debitCount = walletLedgerRepository.findAllByPlayerIdOrderByCreatedAtDesc(player.getId())
                .stream()
                .filter(e -> e.getType() == LedgerType.DEBIT_BET)
                .count();
        assertThat(debitCount).isEqualTo(2);
    }
}
