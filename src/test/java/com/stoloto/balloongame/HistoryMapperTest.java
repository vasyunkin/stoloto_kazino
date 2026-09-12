package com.stoloto.balloongame;

import com.stoloto.balloongame.api.dto.HistoryItemResponse;
import com.stoloto.balloongame.domain.entity.GameRound;
import com.stoloto.balloongame.domain.entity.Player;
import com.stoloto.balloongame.domain.entity.RoundStatus;
import com.stoloto.balloongame.domain.repository.GameRoundRepository;
import com.stoloto.balloongame.service.GameHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S11 — history mapper and query contract (no player JOIN, no secrets in DTO).
 */
class HistoryMapperTest {

    @Test
    void from_mapsPersistedFields_withoutSecrets() {
        GameRound round = new GameRound();
        round.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        round.setStatus(RoundStatus.CASHED_OUT);
        round.setBetAmount(new BigDecimal("100.0000"));
        round.setWinAmount(new BigDecimal("150.0000"));
        round.setPointsEarned(40);
        round.setBalloonType("STANDARD");
        Instant started = Instant.parse("2026-09-01T12:00:00Z");
        Instant ended = Instant.parse("2026-09-01T12:00:05Z");
        round.setStartedAt(started);
        round.setEndedAt(ended);
        round.setCrashPoint(new BigDecimal("2.5000"));
        round.setServerSeed("deadbeef");
        round.setClientSeed("client");
        round.setPlayer(new Player("should-not-appear"));

        HistoryItemResponse dto = HistoryItemResponse.from(round);

        assertThat(dto.gameId()).isEqualTo(round.getId());
        assertThat(dto.status()).isEqualTo(RoundStatus.CASHED_OUT);
        assertThat(dto.betAmount()).isEqualByComparingTo("100.0000");
        assertThat(dto.winAmount()).isEqualByComparingTo("150.0000");
        assertThat(dto.pointsEarned()).isEqualTo(40);
        assertThat(dto.balloonType()).isEqualTo("STANDARD");
        assertThat(dto.startedAt()).isEqualTo(started);
        assertThat(dto.endedAt()).isEqualTo(ended);

        // Record components must not include secret / player fields
        assertThat(HistoryItemResponse.class.getRecordComponents())
                .extracting(rc -> rc.getName())
                .doesNotContain("serverSeed", "crashPoint", "clientSeed", "playerId", "playerExternalId");
    }

    @Test
    void clampLimit_defaultsAndCaps() {
        assertThat(GameHistoryService.clampLimit(null)).isEqualTo(20);
        assertThat(GameHistoryService.clampLimit(0)).isEqualTo(1);
        assertThat(GameHistoryService.clampLimit(-5)).isEqualTo(1);
        assertThat(GameHistoryService.clampLimit(50)).isEqualTo(50);
        assertThat(GameHistoryService.clampLimit(500)).isEqualTo(100);
    }

    @Test
    void findTerminalHistory_queryHasNoPlayerJoin() throws Exception {
        Method method = Arrays.stream(GameRoundRepository.class.getMethods())
                .filter(m -> m.getName().equals("findTerminalHistory"))
                .findFirst()
                .orElseThrow();
        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        String jpql = query.value().toLowerCase();
        assertThat(jpql).contains("from gameround");
        assertThat(jpql).doesNotContain("join");
        assertThat(jpql).doesNotContain("player");
    }
}
