package com.stoloto.balloongame.api.dto;

import com.stoloto.balloongame.domain.entity.GameRound;
import com.stoloto.balloongame.domain.entity.RoundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Public history feed item. No player id and no seeds.
 * {@code crashPoint} is public once the round is terminal — it is the bust result, not a secret.
 */
public record HistoryItemResponse(
        UUID gameId,
        RoundStatus status,
        BigDecimal betAmount,
        BigDecimal winAmount,
        int pointsEarned,
        String balloonType,
        Instant startedAt,
        Instant endedAt,
        BigDecimal crashPoint
) {
    /**
     * Map persisted round fields only — must not touch {@code GameRound.player} (no JOIN / N+1).
     */
    public static HistoryItemResponse from(GameRound round) {
        return new HistoryItemResponse(
                round.getId(),
                round.getStatus(),
                round.getBetAmount(),
                round.getWinAmount(),
                round.getPointsEarned(),
                round.getBalloonType(),
                round.getStartedAt(),
                round.getEndedAt(),
                round.getCrashPoint()
        );
    }
}
