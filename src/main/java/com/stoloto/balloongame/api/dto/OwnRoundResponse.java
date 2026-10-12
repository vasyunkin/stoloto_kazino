package com.stoloto.balloongame.api.dto;

import com.stoloto.balloongame.domain.entity.GameRound;
import com.stoloto.balloongame.domain.entity.RoundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row of the signed-in (or guest) player's own round journal. No server seed. */
public record OwnRoundResponse(
        UUID gameId,
        RoundStatus status,
        BigDecimal betAmount,
        BigDecimal winAmount,
        BigDecimal crashPoint,
        BigDecimal cashoutMultiplier,
        String commitHash,
        String algorithmVersion,
        String balloonType,
        Instant startedAt,
        Instant endedAt
) {
    public static OwnRoundResponse from(GameRound round) {
        return new OwnRoundResponse(
                round.getId(),
                round.getStatus(),
                round.getBetAmount(),
                round.getWinAmount(),
                round.getStatus() == RoundStatus.FLYING ? null : round.getCrashPoint(),
                round.getCashoutMultiplier(),
                round.getServerSeedHash(),
                round.getAlgorithmVersion(),
                round.getBalloonType(),
                round.getStartedAt(),
                round.getEndedAt()
        );
    }
}
