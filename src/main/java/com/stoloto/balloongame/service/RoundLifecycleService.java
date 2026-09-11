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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Single lifecycle path for {@code GET state} and {@code POST cashout} (Spec §5.4).
 *
 * <p>One {@code ReentrantLock} per {@code gameId} covers both intents (I4).
 * Multiplier is computed once per tick via {@link GameClockService#multiplier} (I3)
 * from {@code session.configSnapshot} only (I8).
 *
 * <p>Money writes run in a nested transaction started <em>after</em> the lock is held,
 * so we never join a read-only outer TX and the first DB call of a cashout TX is
 * {@code SELECT FOR UPDATE} on the player (I5).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoundLifecycleService {

    private static final int MONEY_SCALE = 4;

    private final GameSessionCache sessionCache;
    private final GameClockService gameClock;
    private final CrashMathService crashMath;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final PlayerRepository playerRepository;
    private final PlayerService playerService;
    private final GameRoundRepository gameRoundRepository;

    public RoundView resolve(GameSession session, ResolveIntent intent) {
        ReentrantLock lock = sessionCache.lockFor(session.getGameId());
        lock.lock();
        try {
            GameSession current = sessionCache.get(session.getGameId()).orElse(session);

            if (current.getStatus().isTerminal()) {
                if (intent == ResolveIntent.CASHOUT) {
                    throw terminalCashoutException(current.getStatus());
                }
                return viewOfTerminal(current);
            }

            Instant now = clock.instant();
            BigDecimal k = gameClock.multiplier(current, now);
            int line = gameClock.lineIndex(current, now);
            activateBoosterIfDue(current, line);

            if (k.compareTo(current.getCrashPoint()) >= 0) {
                persistCrash(current, now);
                current.setStatus(RoundStatus.CRASHED);
                current.setEndedAt(now);
                // I7: no serverSeed / crashPoint in logs.
                log.info("Round crashed: gameId={}", current.getGameId());
                if (intent == ResolveIntent.CASHOUT) {
                    throw GameException.alreadyCrashed();
                }
                return viewOfTerminal(current);
            }

            if (intent == ResolveIntent.CASHOUT) {
                BigDecimal win = current.getBetAmount()
                        .multiply(k)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                persistCashout(current, k, win, now);
                current.setStatus(RoundStatus.CASHED_OUT);
                current.setCashoutMultiplier(k);
                current.setWinAmount(win);
                current.setEndedAt(now);
                log.info("Cashout OK: gameId={} win={}", current.getGameId(), win);
                return viewOfTerminal(current);
            }

            return flyingView(current, k, line);
        } finally {
            lock.unlock();
        }
    }

    private RoundView flyingView(GameSession session, BigDecimal k, int line) {
        GameConfigSnapshotLines lines = GameConfigSnapshotLines.from(session);
        String zone = crashMath.zoneFor(line, lines.green(), lines.red());
        return new RoundView(session, RoundStatus.FLYING, k, line, zone, 0);
    }

    private RoundView viewOfTerminal(GameSession session) {
        Instant asOf = session.getEndedAt() != null ? session.getEndedAt() : clock.instant();
        int line = gameClock.lineIndex(session, asOf);
        GameConfigSnapshotLines lines = GameConfigSnapshotLines.from(session);
        String zone = session.getStatus() == RoundStatus.CRASHED
                ? "CRASHED"
                : crashMath.zoneFor(line, lines.green(), lines.red());
        BigDecimal display = session.getStatus() == RoundStatus.CASHED_OUT
                && session.getCashoutMultiplier() != null
                ? session.getCashoutMultiplier()
                : session.getCrashPoint();
        return new RoundView(session, session.getStatus(), display, line, zone, 0);
    }

    private void activateBoosterIfDue(GameSession session, int line) {
        if (session.getBoostTriggerLine() == null || session.isBoostActivated()) {
            return;
        }
        if (line >= session.getBoostTriggerLine()) {
            session.setBoostActivated(true);
        }
    }

    private void persistCrash(GameSession session, Instant endedAt) {
        transactionTemplate.executeWithoutResult(status -> {
            GameRound round = gameRoundRepository.findById(session.getGameId())
                    .orElseThrow(GameException::gameNotFound);
            if (round.getStatus() != RoundStatus.FLYING) {
                return;
            }
            round.setStatus(RoundStatus.CRASHED);
            round.setEndedAt(endedAt);
            round.setPointsEarned(0);
        });
    }

    private void persistCashout(GameSession session, BigDecimal multiplier,
                                BigDecimal win, Instant endedAt) {
        transactionTemplate.executeWithoutResult(status -> {
            // FIRST DB call in this write transaction — player row lock (I5).
            var player = playerRepository.findByExternalIdForUpdate(session.getPlayerId())
                    .orElseThrow(GameException::playerNotFound);
            GameRound round = gameRoundRepository.findById(session.getGameId())
                    .orElseThrow(GameException::gameNotFound);
            if (round.getStatus() != RoundStatus.FLYING) {
                throw terminalCashoutException(round.getStatus());
            }
            playerService.creditWin(player, win, round);
            round.setStatus(RoundStatus.CASHED_OUT);
            round.setCashoutMultiplier(multiplier);
            round.setWinAmount(win);
            round.setEndedAt(endedAt);
            round.setPointsEarned(0);
        });
    }

    private static GameException terminalCashoutException(RoundStatus status) {
        if (status == RoundStatus.CASHED_OUT) {
            return GameException.alreadyCashedOut();
        }
        return GameException.alreadyCrashed();
    }

    private record GameConfigSnapshotLines(int green, int red) {
        static GameConfigSnapshotLines from(GameSession session) {
            var cfg = session.getConfigSnapshot();
            return new GameConfigSnapshotLines(cfg.getGreenLevels(), cfg.getRedLevels());
        }
    }
}
