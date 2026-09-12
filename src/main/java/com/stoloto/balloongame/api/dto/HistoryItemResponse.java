package com.stoloto.balloongame.api.dto;

import com.stoloto.balloongame.domain.entity.GameRound;
import com.stoloto.balloongame.domain.entity.RoundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Public history feed item (S11). No player id, no PF secrets (I1).
 */
public record HistoryItemResponse(
        UUID gameId,
        RoundStatus status,
        BigDecimal betAmount,
        BigDecimal winAmount,
        int pointsEarned,
        String balloonType,
        Instant startedAt,
        Instant endedAt
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
                round.getEndedAt()
        );
    }
}
