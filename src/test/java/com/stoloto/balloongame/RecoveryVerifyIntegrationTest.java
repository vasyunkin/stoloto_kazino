package com.stoloto.balloongame;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stoloto.balloongame.api.dto.BetRequest;
import com.stoloto.balloongame.api.dto.StartGameResponse;
import com.stoloto.balloongame.api.dto.VerifyResponse;
import com.stoloto.balloongame.config.GameConfig;
import com.stoloto.balloongame.domain.entity.GameRound;
import com.stoloto.balloongame.domain.entity.LedgerType;
import com.stoloto.balloongame.domain.entity.Player;
import com.stoloto.balloongame.domain.entity.RoundStatus;
import com.stoloto.balloongame.domain.repository.GameRoundRepository;
import com.stoloto.balloongame.domain.repository.PlayerRepository;
import com.stoloto.balloongame.domain.repository.WalletLedgerRepository;
import com.stoloto.balloongame.provablyfair.ProvablyFairService;
import com.stoloto.balloongame.service.CrashMathService;
import com.stoloto.balloongame.service.GameOrchestrator;
import com.stoloto.balloongame.service.GameSession;
import com.stoloto.balloongame.service.GameSessionCache;
import com.stoloto.balloongame.service.PlayerService;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S7 — startup recovery, terminal cache TTL, Provably Fair verify.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class RecoveryVerifyIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-08-01T12:00:00Z");
    private static final BigDecimal MIN_FLYING_CRASH = new BigDecimal("1.0500");

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
    @Autowired RoundRecoveryService recoveryService;
    @Autowired ProvablyFairService provablyFairService;
    @Autowired CrashMathService crashMathService;

    @AfterEach
    void resetClock() {
        clock.useSystemUtc();
    }

    private static String uid() {
        return "s7-" + UUID.randomUUID();
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

    @Test
    void recovery_flyingRound_voidedAndRefunded() throws Exception {
        FlyingRound flying = flyingRound();
        assertThat(playerService.getBalance(flying.playerId())).isEqualByComparingTo("900.00");

        int n = recoveryService.recoverAllFlying();
        assertThat(n).isGreaterThanOrEqualTo(1);

        GameRound persisted = gameRoundRepository.findById(flying.gameId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(RoundStatus.VOID);
        assertThat(persisted.getEndedAt()).isNotNull();
        assertThat(sessionCache.get(flying.gameId())).isEmpty();
        assertThat(playerService.getBalance(flying.playerId())).isEqualByComparingTo("1000.00");

        Player player = playerRepository.findByExternalId(flying.playerId()).orElseThrow();
        assertThat(player.getBalance())
                .isEqualByComparingTo(walletLedgerRepository.sumBalanceByPlayerId(player.getId()));
        long refunds = walletLedgerRepository.findAllByPlayerIdOrderByCreatedAtDesc(player.getId())
                .stream()
                .filter(e -> e.getType() == LedgerType.CREDIT_REFUND)
                .count();
        assertThat(refunds).isEqualTo(1);

        mockMvc.perform(get("/api/game/state/" + flying.gameId())
                        .header("X-Player-Id", flying.playerId()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("ROUND_EXPIRED"));

        mockMvc.perform(get("/api/game/verify/" + flying.gameId())
                        .header("X-Player-Id", flying.playerId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithmVersion").value("crash-v1"))
                .andExpect(jsonPath("$.serverSeed").value(flying.round().getServerSeed()));

        recoveryService.recoverAllFlying();
        assertThat(walletLedgerRepository.findAllByPlayerIdOrderByCreatedAtDesc(player.getId())
                .stream()
                .filter(e -> e.getType() == LedgerType.CREDIT_REFUND)
                .count()).isEqualTo(1);
    }

    @Test
    void verify_whileFlying_returns409_andDoesNotLeakSeed() throws Exception {
        FlyingRound flying = flyingRound();

        MvcResult result = mockMvc.perform(get("/api/game/verify/" + flying.gameId())
                        .header("X-Player-Id", flying.playerId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_NOT_TERMINAL"))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertThat(json).doesNotContain(flying.round().getServerSeed());
        assertThat(json).doesNotContain("\"serverSeed\"");
    }

    @Test
    void verify_afterCashout_matchesIndependentCrashAndCommit() throws Exception {
        FlyingRound flying = flyingRound();
        orchestrator.cashout(flying.gameId(), flying.playerId());

        MvcResult result = mockMvc.perform(get("/api/game/verify/" + flying.gameId())
                        .header("X-Player-Id", flying.playerId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithmVersion").value("crash-v1"))
                .andReturn();

        VerifyResponse verify = objectMapper.readValue(
                result.getResponse().getContentAsString(), VerifyResponse.class);
        GameRound round = gameRoundRepository.findById(flying.gameId()).orElseThrow();

        assertThat(verify.serverSeed()).isEqualTo(round.getServerSeed());
        assertThat(verify.crashPoint()).isEqualByComparingTo(round.getCrashPoint());
        assertThat(verify.commitHash()).isEqualTo(round.getServerSeedHash());
        assertThat(verify.nonce()).isEqualTo(round.getNonce());

        String expectedCommit = sha256(verify.serverSeed() + "|" + verify.clientSeed() + "|" + verify.nonce());
        assertThat(verify.commitHash()).isEqualTo(expectedCommit);
        assertThat(provablyFairService.verify(
                verify.serverSeed(), verify.clientSeed(), verify.nonce(),
                verify.commitHash(), "SHA-256")).isTrue();

        GameConfig snapshot = objectMapper.readValue(round.getGameConfigSnapshot(), GameConfig.class);
        BigDecimal recomputed = crashMathService.drawCrashMultiplier(
                verify.serverSeed(), verify.clientSeed(), verify.nonce(), snapshot);
        assertThat(recomputed).isEqualByComparingTo(verify.crashPoint());
    }

    @Test
    void verify_foreignPlayer_returns403() throws Exception {
        FlyingRound flying = flyingRound();
        orchestrator.cashout(flying.gameId(), flying.playerId());

        mockMvc.perform(get("/api/game/verify/" + flying.gameId())
                        .header("X-Player-Id", "intruder"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void verify_unknownGame_returns404() throws Exception {
        mockMvc.perform(get("/api/game/verify/" + UUID.randomUUID())
                        .header("X-Player-Id", uid()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));
    }

    @Test
    void state_rebuildsTerminalSessionAfterCacheEvict() throws Exception {
        FlyingRound flying = flyingRound();
        orchestrator.cashout(flying.gameId(), flying.playerId());
        sessionCache.evict(flying.gameId());

        var state = orchestrator.state(flying.gameId(), flying.playerId());
        assertThat(state.status()).isEqualTo(RoundStatus.CASHED_OUT);
        assertThat(state.serverSeed()).isEqualTo(flying.round().getServerSeed());
    }

    @Test
    void cache_terminalTtlEviction_stillServesStateFromDb() throws Exception {
        FlyingRound flying = flyingRound();
        orchestrator.cashout(flying.gameId(), flying.playerId());

        GameSession session = sessionCache.get(flying.gameId()).orElseThrow();
        session.setEndedAt(clock.instant().minus(GameSessionCache.TERMINAL_TTL).minusSeconds(5));
        assertThat(sessionCache.get(flying.gameId())).isEmpty();

        var state = orchestrator.state(flying.gameId(), flying.playerId());
        assertThat(state.status()).isEqualTo(RoundStatus.CASHED_OUT);
    }

    private static String sha256(String preimage) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(preimage.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
