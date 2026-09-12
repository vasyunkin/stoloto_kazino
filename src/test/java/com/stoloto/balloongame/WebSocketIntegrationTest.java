package com.stoloto.balloongame;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stoloto.balloongame.api.dto.BetRequest;
import com.stoloto.balloongame.api.dto.StartGameResponse;
import com.stoloto.balloongame.api.dto.WsGameEvent;
import com.stoloto.balloongame.domain.entity.GameRound;
import com.stoloto.balloongame.domain.entity.RoundStatus;
import com.stoloto.balloongame.domain.repository.GameRoundRepository;
import com.stoloto.balloongame.service.CrashMathService;
import com.stoloto.balloongame.service.GameSessionCache;
import com.stoloto.balloongame.service.PlayerService;
import com.stoloto.balloongame.support.MutableClock;
import com.stoloto.balloongame.ws.GameWsSubscriptionRegistry;
import com.stoloto.balloongame.ws.StompPlayerInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S14 — STOMP ticks use PublicGameState (I1); ownership on subscribe (I6); REST polling intact.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class WebSocketIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-12T16:00:00Z");
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

    @LocalServerPort
    int port;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired MutableClock clock;
    @Autowired PlayerService playerService;
    @Autowired GameRoundRepository gameRoundRepository;
    @Autowired GameSessionCache sessionCache;
    @Autowired CrashMathService crashMath;
    @Autowired GameWsSubscriptionRegistry registry;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUpClient() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);
    }

    @AfterEach
    void resetClock() {
        clock.useSystemUtc();
        // Clear watches between tests so ticker noise does not leak.
        registry.snapshot().keySet().forEach(registry::unregister);
    }

    private static String uid() {
        return "s14-" + UUID.randomUUID();
    }

    private String wsUrl() {
        return "ws://localhost:" + port + "/ws";
    }

    private StompSession connect(String playerId) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("X-Player-Id", playerId);
        return stompClient.connectAsync(
                        wsUrl(),
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
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

    private void freezePastCrash(GameRound round) {
        double r = sessionCache.get(round.getId())
                .map(s -> s.getConfigSnapshot().getMath().getGrowthRate())
                .orElseThrow();
        double tCrash = crashMath.timeAtMultiplier(round.getCrashPoint().doubleValue(), r);
        long millis = Math.max(1L, (long) Math.ceil(tCrash * 1000.0) + 25L);
        clock.freeze(round.getStartedAt().plusMillis(millis));
        int guard = 0;
        while (crashMath.multiplierAt(round.getStartedAt(), clock.instant(), r)
                .compareTo(round.getCrashPoint()) < 0 && guard++ < 30) {
            clock.freeze(clock.instant().plusMillis(50));
        }
    }

    private BlockingQueue<WsGameEvent> subscribeQueue(StompSession session, UUID gameId) {
        BlockingQueue<WsGameEvent> queue = new LinkedBlockingQueue<>();
        session.subscribe(StompPlayerInterceptor.TOPIC_PREFIX + gameId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return WsGameEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.offer((WsGameEvent) payload);
            }
        });
        return queue;
    }

    @Test
    void tick_whileFlying_hasNoSecrets() throws Exception {
        FlyingRound flying = flyingRound();
        StompSession session = connect(flying.playerId());
        BlockingQueue<WsGameEvent> queue = subscribeQueue(session, flying.gameId());

        WsGameEvent event = queue.poll(5, TimeUnit.SECONDS);
        assertThat(event)
                .as("expected STOMP tick within 5s; watched=%s", registry.isWatched(flying.gameId()))
                .isNotNull();
        assertThat(event.type()).isEqualTo("tick");
        assertThat(event.state().status()).isEqualTo(RoundStatus.FLYING);
        assertThat(event.state().gameId()).isEqualTo(flying.gameId());
        assertThat(event.state().multiplier()).isNotNull();

        String json = objectMapper.writeValueAsString(event);
        assertThat(json)
                .doesNotContain("crashPoint")
                .doesNotContain("serverSeed")
                .doesNotContain("clientSeed");

        if (session.isConnected()) {
            session.disconnect();
        }
    }

    @Test
    void afterCrash_wsEmitsCrash_andRestVerifyWorks() throws Exception {
        FlyingRound flying = flyingRound();
        StompSession session = connect(flying.playerId());
        BlockingQueue<WsGameEvent> queue = subscribeQueue(session, flying.gameId());

        freezePastCrash(flying.round());

        WsGameEvent crashEvent = null;
        for (int i = 0; i < 20; i++) {
            WsGameEvent event = queue.poll(1, TimeUnit.SECONDS);
            if (event != null && "crash".equals(event.type())) {
                crashEvent = event;
                break;
            }
        }
        assertThat(crashEvent).isNotNull();
        assertThat(crashEvent.state().status()).isEqualTo(RoundStatus.CRASHED);
        String json = objectMapper.writeValueAsString(crashEvent);
        assertThat(json).doesNotContain("serverSeed").doesNotContain("crashPoint");

        mockMvc.perform(get("/api/game/verify/" + flying.gameId())
                        .header("X-Player-Id", flying.playerId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverSeed").exists())
                .andExpect(jsonPath("$.crashPoint").exists());

        mockMvc.perform(get("/api/game/state/" + flying.gameId())
                        .header("X-Player-Id", flying.playerId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CRASHED"));

        session.disconnect();
    }

    @Test
    void foreignPlayer_subscribeDoesNotRegisterWatch() throws Exception {
        FlyingRound flying = flyingRound();
        String stranger = uid();
        playerService.deposit(stranger, new BigDecimal("100"));

        AtomicReference<Throwable> transportError = new AtomicReference<>();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("X-Player-Id", stranger);
        StompSession session = stompClient.connectAsync(
                        wsUrl(),
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {
                            @Override
                            public void handleTransportError(StompSession session, Throwable exception) {
                                transportError.set(exception);
                            }
                        })
                .get(5, TimeUnit.SECONDS);

        session.subscribe(StompPlayerInterceptor.TOPIC_PREFIX + flying.gameId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return WsGameEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
            }
        });

        Thread.sleep(400);
        assertThat(registry.isWatched(flying.gameId()))
                .as("stranger must not receive a watch registration (I6)")
                .isFalse();

        if (session.isConnected()) {
            session.disconnect();
        }
    }

    @Test
    void connectWithoutPlayerId_fails() {
        StompHeaders connectHeaders = new StompHeaders();
        assertThatThrownBy(() -> stompClient.connectAsync(
                        wsUrl(),
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS))
                .isInstanceOf(Exception.class);
    }
}
