package com.stoloto.balloongame;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stoloto.balloongame.api.dto.BetRequest;
import com.stoloto.balloongame.api.dto.GameStateResponse;
import com.stoloto.balloongame.api.dto.StartGameResponse;
import com.stoloto.balloongame.domain.entity.GameRound;
import com.stoloto.balloongame.domain.entity.Player;
import com.stoloto.balloongame.domain.repository.ConfigSnapshotRepository;
import com.stoloto.balloongame.domain.repository.GameRoundRepository;
import com.stoloto.balloongame.domain.repository.PlayerRepository;
import com.stoloto.balloongame.domain.repository.WalletLedgerRepository;
import com.stoloto.balloongame.service.CrashMathService;
import com.stoloto.balloongame.service.GameOrchestrator;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S8 — admin runtime config. X-Admin-Key required. I8: PUT must not move a FLYING round.
 * S10 — allowDeposit via ConfigService snapshot; malformed JSON codes by path.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class AdminConfigIntegrationTest {

    private static final String ADMIN_KEY = "test-admin-key";
    private static final Instant T0 = Instant.parse("2026-09-01T12:00:00Z");
    private static final BigDecimal MIN_CRASH = new BigDecimal("2.0000");

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
    @Autowired WalletLedgerRepository walletLedgerRepository;
    @Autowired GameRoundRepository gameRoundRepository;
    @Autowired ConfigSnapshotRepository configSnapshotRepository;
    @Autowired GameOrchestrator orchestrator;
    @Autowired CrashMathService crashMath;
    @Autowired GameSessionCache sessionCache;

    @AfterEach
    void resetClockAndGrowthRate() throws Exception {
        clock.useSystemUtc();
        putConfig("{\"math\":{\"growthRate\":0.0433},"
                + "\"themes\":{\"standard\":{\"growthRate\":0.0367},\"lucky\":{\"growthRate\":0.0567}},"
                + "\"admin\":{\"allowDeposit\":true}}");
    }

    private void putConfig(String json) throws Exception {
        mockMvc.perform(put("/api/admin/config")
                        .header("X-Admin-Key", ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    private static String uid() {
        return "s8-" + UUID.randomUUID();
    }

    private record FlyingRound(String playerId, UUID gameId, GameRound round) {}

    private FlyingRound flyingRound() throws Exception {
        for (int i = 0; i < 60; i++) {
            clock.freeze(T0);
            String playerId = uid();
            playerService.deposit(playerId, new BigDecimal("1000.00"));
            StartGameResponse start = startOk(playerId);
            GameRound round = gameRoundRepository.findById(start.gameId()).orElseThrow();
            if (round.getCrashPoint().compareTo(MIN_CRASH) >= 0) {
                return new FlyingRound(playerId, start.gameId(), round);
            }
        }
        throw new AssertionError("Could not draw crashPoint >= " + MIN_CRASH);
    }

    private StartGameResponse startOk(String playerId) throws Exception {
        BetRequest body = new BetRequest(playerId, new BigDecimal("100"), "STANDARD", "NONE", null);
        MvcResult result = mockMvc.perform(post("/api/game/start")
                        .header("X-Player-Id", playerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), StartGameResponse.class);
    }

    @Test
    void getConfig_withoutKey_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/config"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    void getConfig_withKey_omitsApiKey() throws Exception {
        mockMvc.perform(get("/api/admin/config")
                        .header("X-Admin-Key", ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.math.growthRate").exists())
                .andExpect(jsonPath("$.admin.minBetAmount").exists())
                .andExpect(jsonPath("$.admin.apiKey").doesNotExist());
    }

    @Test
    void putConfig_invalidJson_returnsCONFIG_VALIDATION_FAILED() throws Exception {
        mockMvc.perform(put("/api/admin/config")
                        .header("X-Admin-Key", ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONFIG_VALIDATION_FAILED"));
    }

    @Test
    void putConfig_invalidHouseEdge_returnsCONFIG_VALIDATION_FAILED() throws Exception {
        mockMvc.perform(put("/api/admin/config")
                        .header("X-Admin-Key", ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"math\":{\"houseEdge\":0.9}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONFIG_VALIDATION_FAILED"));
    }

    @Test
    void putConfig_persistsSnapshot_andGetReflectsPatch() throws Exception {
        long before = configSnapshotRepository.count();
        putConfig("{\"math\":{\"growthRate\":0.5}}");
        assertThat(configSnapshotRepository.count()).isEqualTo(before + 1);

        mockMvc.perform(get("/api/admin/config")
                        .header("X-Admin-Key", ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.math.growthRate").value(0.5))
                .andExpect(jsonPath("$.math.houseEdge").exists());
    }

    @Test
    void putGrowthRate_flyingKeepsOldK_newStartUsesNewR() throws Exception {
        FlyingRound first = flyingRound();
        putConfig("{\"themes\":{\"standard\":{\"growthRate\":0.5}}}");
        FlyingRound second = flyingRound();

        Instant later = T0.plusSeconds(1);
        clock.freeze(later);

        GameStateResponse stateA = orchestrator.state(first.gameId(), first.playerId());
        GameStateResponse stateB = orchestrator.state(second.gameId(), second.playerId());

        double rateOld = sessionCache.get(first.gameId()).orElseThrow()
                .getConfigSnapshot().getMath().getGrowthRate();
        BigDecimal expectedOld = crashMath.multiplierAt(first.round().getStartedAt(), later, rateOld);
        BigDecimal expectedNew = crashMath.multiplierAt(second.round().getStartedAt(), later, 0.5);

        assertThat(stateA.multiplier()).isEqualByComparingTo(expectedOld);
        assertThat(stateB.multiplier()).isEqualByComparingTo(expectedNew);
        assertThat(stateA.multiplier()).isNotEqualByComparingTo(stateB.multiplier());
    }

    // ── S10: allowDeposit via ConfigService + JSON error codes ────────────

    @Test
    void putAllowDepositFalse_depositReturns403_andNoLedger() throws Exception {
        String playerId = uid();
        playerService.deposit(playerId, new BigDecimal("100.00"));
        Player player = playerRepository.findByExternalId(playerId).orElseThrow();
        long ledgerBefore = walletLedgerRepository.countByPlayerId(player.getId());

        putConfig("{\"admin\":{\"allowDeposit\":false}}");

        mockMvc.perform(post("/api/players/{id}/deposit", playerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50.00}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEPOSIT_NOT_ALLOWED"));

        assertThat(walletLedgerRepository.countByPlayerId(player.getId())).isEqualTo(ledgerBefore);
        assertThat(playerService.getBalance(playerId)).isEqualByComparingTo("100.00");
    }

    @Test
    void putAllowDepositTrue_depositReturns200() throws Exception {
        String playerId = uid();
        putConfig("{\"admin\":{\"allowDeposit\":false}}");
        putConfig("{\"admin\":{\"allowDeposit\":true}}");

        mockMvc.perform(post("/api/players/{id}/deposit", playerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":75.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newBalance").value(75.00));
    }

    @Test
    void postGameStart_invalidJson_returnsVALIDATION_ERROR_notConfigCode() throws Exception {
        mockMvc.perform(post("/api/game/start")
                        .header("X-Player-Id", uid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
