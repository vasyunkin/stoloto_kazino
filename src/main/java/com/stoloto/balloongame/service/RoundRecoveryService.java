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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Keeps FLYING rounds on the server clock. A restart resumes a round from its stored
 * crash point and snapshot. The bet is refunded only when that snapshot cannot be read.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoundRecoveryService {

    private final GameRoundRepository gameRoundRepository;
    private final PlayerRepository playerRepository;
    private final PlayerService playerService;
    private final GameSessionCache sessionCache;
    private final GameOrchestrator orchestrator;
    private final RoundLifecycleService lifecycle;
    private final TransactionTemplate transactionTemplate;
    private final java.time.Clock clock;

    /** Startup and the live settler share this sweep. */
    public int settleLiveRounds() {
        List<UUID> ids = gameRoundRepository.findIdsByStatus(RoundStatus.FLYING);
        for (UUID id : ids) {
            settleOne(id);
        }
        return ids.size();
    }

    /** @deprecated use {@link #settleLiveRounds()}; kept so older call sites stay source-compatible. */
    public int recoverAllFlying() {
        return settleLiveRounds();
    }

    private void settleOne(UUID roundId) {
        ReentrantLock lock = sessionCache.lockFor(roundId);
        lock.lock();
        try {
            try {
                var session = orchestrator.restoreFlying(roundId);
                session.ifPresent(value -> lifecycle.resolve(value, ResolveIntent.STATE_ONLY));
            } catch (IllegalStateException ex) {
                log.warn("Cannot resume round {}: {}", roundId, ex.getMessage());
                voidAndRefund(roundId);
                sessionCache.evict(roundId);
            }
        } finally {
            lock.unlock();
        }
    }

    private void voidAndRefund(UUID roundId) {
        transactionTemplate.executeWithoutResult(status -> {
            String externalId = gameRoundRepository.findPlayerExternalIdById(roundId).orElse(null);
            if (externalId == null) {
                return;
            }
            var player = playerRepository.findByExternalIdForUpdate(externalId)
                    .orElseThrow(GameException::playerNotFound);
            GameRound round = gameRoundRepository.findById(roundId).orElse(null);
            if (round == null || round.getStatus() != RoundStatus.FLYING) {
                return;
            }
            playerService.refundBet(player, round.getBetAmount(), round);
            round.setStatus(RoundStatus.VOID);
            round.setEndedAt(clock.instant());
            log.info("Voided unreadable round: gameId={} playerId={} refund={}",
                    roundId, externalId, round.getBetAmount());
        });
    }
}
