package com.stoloto.balloongame;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stoloto.balloongame.api.dto.LeaderboardEntryResponse;
import com.stoloto.balloongame.domain.entity.GameRound;
import com.stoloto.balloongame.domain.entity.Player;
import com.stoloto.balloongame.domain.entity.RoundStatus;
import com.stoloto.balloongame.domain.repository.GameRoundRepository;
import com.stoloto.balloongame.domain.repository.PlayerRepository;
import com.stoloto.balloongame.service.PlayerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S15 — public leaderboard by sum of points_earned. No balances / PF secrets.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class LeaderboardIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PlayerService playerService;
    @Autowired PlayerRepository playerRepository;
    @Autowired GameRoundRepository gameRoundRepository;

    @Test
    void leaderboard_empty_whenNoRounds() throws Exception {
        mockMvc.perform(get("/api/players/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void leaderboard_ordersByPointsDesc_noSecrets() throws Exception {
        String low = "lb-low-" + UUID.randomUUID();
        String high = "lb-high-" + UUID.randomUUID();
        playerService.deposit(low, new BigDecimal("100"));
        playerService.deposit(high, new BigDecimal("100"));

        Player pLow = playerRepository.findByExternalId(low).orElseThrow();
        Player pHigh = playerRepository.findByExternalId(high).orElseThrow();
        persistTerminal(pLow, 30);
        persistTerminal(pHigh, 90);
        persistTerminal(pHigh, 10); // high total = 100

        MvcResult result = mockMvc.perform(get("/api/players/leaderboard").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].externalId").value(high))
                .andExpect(jsonPath("$[0].totalPoints").value(100))
                .andExpect(jsonPath("$[0].rank").value(1))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertThat(json)
                .doesNotContain("serverSeed")
                .doesNotContain("crashPoint")
                .doesNotContain("balance");

        List<LeaderboardEntryResponse> rows = objectMapper.readValue(
                json, new TypeReference<>() {});
        assertThat(rows.stream().map(LeaderboardEntryResponse::externalId))
                .contains(high, low);
        assertThat(rows.getFirst().totalPoints()).isGreaterThanOrEqualTo(rows.get(1).totalPoints());
    }

    private void persistTerminal(Player player, int points) {
        GameRound round = new GameRound();
        round.setPlayer(player);
        round.setBetAmount(new BigDecimal("10.0000"));
        round.setBalloonType("STANDARD");
        round.setStatus(RoundStatus.CRASHED);
        round.setCrashPoint(new BigDecimal("2.0000"));
        round.setServerSeed("cd".repeat(32));
        round.setServerSeedHash("ef".repeat(32));
        round.setClientSeed("");
        round.setNonce(1L);
        round.setPointsEarned(points);
        round.setStartedAt(Instant.parse("2026-09-12T12:00:00Z"));
        round.setEndedAt(Instant.parse("2026-09-12T12:01:00Z"));
        gameRoundRepository.saveAndFlush(round);
    }
}
