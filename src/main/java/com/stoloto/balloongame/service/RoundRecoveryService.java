package com.stoloto.balloongame.service;

import com.stoloto.balloongame.api.exception.GameException;
import com.stoloto.balloongame.domain.entity.GameRound;
import com.stoloto.balloongame.domain.entity.RoundStatus;
import com.stoloto.balloongame.domain.repository.GameRoundRepository;
import com.stoloto.balloongame.domain.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Startup recovery: unfinished {@code FLYING} rounds are voided and the bet is refunded
 * (Spec 0 §4.1). Per-round lock order matches cashout: {@code gameId} lock, then player
 * {@code SELECT FOR UPDATE} as the first entity load in the write TX (I5).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoundRecoveryService {

    private final GameRoundRepository gameRoundRepository;
    private final PlayerRepository playerRepository;
    private final PlayerService playerService;
    private final GameSessionCache sessionCache;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * @return number of FLYING rounds found at the start of the sweep
     */
    public int recoverAllFlying() {
        List<UUID> ids = gameRoundRepository.findIdsByStatus(RoundStatus.FLYING);
        for (UUID id : ids) {
            recoverOne(id);
        }
        return ids.size();
    }

    private void recoverOne(UUID roundId) {
        ReentrantLock lock = sessionCache.lockFor(roundId);
        lock.lock();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                String externalId = gameRoundRepository.findPlayerExternalIdById(roundId)
                        .orElse(null);
                if (externalId == null) {
                    return;
                }
                // FIRST Player entity load in this TX — pessimistic lock (I5).
                var player = playerRepository.findByExternalIdForUpdate(externalId)
                        .orElseThrow(GameException::playerNotFound);
                GameRound round = gameRoundRepository.findById(roundId).orElse(null);
                if (round == null || round.getStatus() != RoundStatus.FLYING) {
                    return;
                }
                playerService.refundBet(player, round.getBetAmount(), round);
                round.setStatus(RoundStatus.VOID);
                round.setEndedAt(clock.instant());
                log.info("Recovered flying round: gameId={} playerId={} refund={}",
                        roundId, externalId, round.getBetAmount());
            });
        } finally {
            lock.unlock();
            sessionCache.evict(roundId);
        }
    }
}
