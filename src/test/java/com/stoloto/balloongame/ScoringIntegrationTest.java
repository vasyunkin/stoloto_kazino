package com.stoloto.balloongame;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stoloto.balloongame.api.dto.BetRequest;
import com.stoloto.balloongame.api.dto.CashoutResponse;
import com.stoloto.balloongame.api.dto.GameStateResponse;
import com.stoloto.balloongame.api.dto.StartGameResponse;
import com.stoloto.balloongame.config.GameConfig;
import com.stoloto.balloongame.domain.entity.GameRound;
import com.stoloto.balloongame.domain.repository.GameRoundRepository;
import com.stoloto.balloongame.service.CrashMathService;
import com.stoloto.balloongame.service.GameOrchestrator;
import com.stoloto.balloongame.service.GameSession;
import com.stoloto.balloongame.service.GameSessionCache;
import com.stoloto.balloongame.service.PlayerService;
import com.stoloto.balloongame.service.ScoringService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S6 — points and booster in state/cashout. RTP/money paths unchanged.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class ScoringIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-07-01T12:00:00Z");
    /** K(4s) ≈ 1.30; stay FLYING while scoring several lines. */
    private static final BigDecimal MIN_FLYING_CRASH = new BigDecimal("1.6000");

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
    @Autowired GameOrchestrator orchestrator;
    @Autowired GameSessionCache sessionCache;
    @Autowired ScoringService scoringService;
    @Autowired CrashMathService crashMath;
    @Autowired GameConfig gameConfig;

    @AfterEach
    void resetClock() {
        clock.useSystemUtc();
    }

    private static String uid() {
        return "s6-" + UUID.randomUUID();
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

    private void freezeAtLine(GameRound round, int line) {
        double speed = sessionCache.get(round.getId())
                .map(s -> s.getConfigSnapshot().getAscentSpeedLinesPerSec())
                .orElse(gameConfig.getAscentSpeedLinesPerSec());
        // t such that floor(t * speed) == line → use the midpoint of that line window
        double t = (line + 0.5) / speed;
        clock.freeze(round.getStartedAt().plusMillis((long) (t * 1000.0)));
        int actual = crashMath.lineIndexAt(round.getStartedAt(), clock.instant(), speed);
        assertThat(actual).isEqualTo(line);
    }

    private int expectedPoints(GameSession session, int line) {
        int linePts = scoringService.pointsForNewLines(0, line, session.getConfigSnapshot());
        if (session.getBoostTriggerLine() != null && line >= session.getBoostTriggerLine()) {
            BigDecimal mult = session.getBoostMultiplier() == null
                    ? BigDecimal.ONE
                    : session.getBoostMultiplier();
            linePts += BigDecimal.valueOf(session.getConfigSnapshot().getPointsPerLine())
                    .multiply(mult)
                    .setScale(0, java.math.RoundingMode.HALF_UP)
                    .intValue();
        }
        return linePts;
    }

    @Test
    void state_pointsDelta_awardedOnNewLines_zeroOnRepeatPoll() throws Exception {
        FlyingRound flying = flyingRound();
        freezeAtLine(flying.round(), 2);
        GameStateResponse early = orchestrator.state(flying.gameId(), flying.playerId());
        assertThat(early.pointsDelta()).isEqualTo(early.pointsTotal());
        assertThat(early.pointsDelta()).isPositive();

        GameStateResponse repeat = orchestrator.state(flying.gameId(), flying.playerId());
        assertThat(repeat.pointsTotal()).isEqualTo(early.pointsTotal());
        assertThat(repeat.pointsDelta()).isZero();

        freezeAtLine(flying.round(), 4);
        GameStateResponse later = orchestrator.state(flying.gameId(), flying.playerId());
        assertThat(later.pointsDelta()).isEqualTo(later.pointsTotal() - early.pointsTotal());
        assertThat(later.pointsDelta()).isPositive();
    }

    @Test
    void state_tenPollsAtSameLine_pointsDoNotGrow() throws Exception {
        FlyingRound flying = flyingRound();
        freezeAtLine(flying.round(), 3);

        GameStateResponse first = orchestrator.state(flying.gameId(), flying.playerId());
        assertThat(first.lineIndex()).isEqualTo(3);
        GameSession session = sessionCache.get(flying.gameId()).orElseThrow();
        int expected = expectedPoints(session, 3);
        assertThat(first.pointsTotal()).isEqualTo(expected);

        for (int i = 0; i < 10; i++) {
            GameStateResponse poll = orchestrator.state(flying.gameId(), flying.playerId());
            assertThat(poll.pointsTotal())
                    .as("poll %s must not add points without new lines", i)
                    .isEqualTo(first.pointsTotal());
            assertThat(poll.pointsDelta()).isZero();
            assertThat(poll.lineIndex()).isEqualTo(3);
        }
    }

    @Test
    void cashout_returnsFinalPointsTotal_matchingDb() throws Exception {
        FlyingRound flying = flyingRound();
        freezeAtLine(flying.round(), 4);

        GameStateResponse state = orchestrator.state(flying.gameId(), flying.playerId());
        CashoutResponse cashout = orchestrator.cashout(flying.gameId(), flying.playerId());

        assertThat(cashout.pointsTotal()).isEqualTo(state.pointsTotal());
        GameRound persisted = gameRoundRepository.findById(flying.gameId()).orElseThrow();
        assertThat(persisted.getPointsEarned()).isEqualTo(cashout.pointsTotal());
        assertThat(cashout.pointsTotal()).isEqualTo(
                expectedPoints(sessionCache.get(flying.gameId()).orElseThrow(), 4));
    }

    @Test
    void state_newLinesIncreasePoints_boosterBonusOnce() throws Exception {
        FlyingRound flying = flyingRound();
        freezeAtLine(flying.round(), 2);
        GameStateResponse early = orchestrator.state(flying.gameId(), flying.playerId());
        GameSession session = sessionCache.get(flying.gameId()).orElseThrow();
        assertThat(early.pointsTotal()).isEqualTo(expectedPoints(session, 2));

        freezeAtLine(flying.round(), 5);
        GameStateResponse later = orchestrator.state(flying.gameId(), flying.playerId());
        assertThat(later.pointsTotal()).isEqualTo(expectedPoints(session, 5));
        assertThat(later.pointsTotal()).isGreaterThan(early.pointsTotal());

        GameStateResponse again = orchestrator.state(flying.gameId(), flying.playerId());
        assertThat(again.pointsTotal()).isEqualTo(later.pointsTotal());

        if (session.getBoostTriggerLine() != null && session.getBoostTriggerLine() <= 5) {
            assertThat(later.booster()).isNotNull();
            assertThat(later.booster().activated()).isTrue();
            assertThat(again.booster().activated()).isTrue();
        }
    }

    @Test
    void state_scoringUsesSnapshot_notLivePointsPerLine() throws Exception {
        FlyingRound flying = flyingRound();
        int original = gameConfig.getPointsPerLine();
        try {
            gameConfig.setPointsPerLine(999);
            freezeAtLine(flying.round(), 3);
            GameStateResponse state = orchestrator.state(flying.gameId(), flying.playerId());
            GameSession session = sessionCache.get(flying.gameId()).orElseThrow();
            assertThat(session.getConfigSnapshot().getPointsPerLine()).isEqualTo(original);
            assertThat(state.pointsTotal()).isEqualTo(expectedPoints(session, 3));
            assertThat(state.pointsTotal()).isLessThan(999);
        } finally {
            gameConfig.setPointsPerLine(original);
        }
    }

    @Test
    void getStateHttp_includesBoosterDtoWhenSpawned() throws Exception {
        FlyingRound flying = flyingRound();
        freezeAtLine(flying.round(), 1);
        MvcResult result = mockMvc.perform(get("/api/game/state/" + flying.gameId())
                        .header("X-Player-Id", flying.playerId()))
                .andExpect(status().isOk())
                .andReturn();
        GameStateResponse state = objectMapper.readValue(
                result.getResponse().getContentAsString(), GameStateResponse.class);
        assertThat(state.pointsTotal()).isGreaterThanOrEqualTo(0);
        if (state.booster() != null) {
            assertThat(state.booster().spawned()).isTrue();
            assertThat(state.booster().tier()).isPositive();
        }
    }
}
