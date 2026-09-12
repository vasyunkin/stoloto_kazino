package com.stoloto.balloongame.service;

import com.stoloto.balloongame.api.exception.GameException;
import com.stoloto.balloongame.config.GameConfig;
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
    private final ScoringService scoringService;
    private final PuzzleService puzzleService;

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

            int scoredLine = line;
            if (k.compareTo(current.getCrashPoint()) >= 0) {
                GameConfig snap = current.getConfigSnapshot();
                scoredLine = crashMath.lineForMultiplier(
                        current.getCrashPoint().doubleValue(),
                        snap.getMath().getGrowthRate(),
                        snap.getAscentSpeedLinesPerSec());
            }
            applyScoring(current, scoredLine);

            if (k.compareTo(current.getCrashPoint()) >= 0) {
                int piece = awardPuzzle(current, RoundStatus.CRASHED, null);
                persistCrash(current, now, piece);
                current.setStatus(RoundStatus.CRASHED);
                current.setEndedAt(now);
                current.setPuzzlePieceIndex(piece);
                // I7: no serverSeed / crashPoint in logs.
                log.info("Round crashed: gameId={} puzzlePiece={}", current.getGameId(), piece);
                if (intent == ResolveIntent.CASHOUT) {
                    throw GameException.alreadyCrashed();
                }
                return viewOfTerminal(current);
            }

            if (intent == ResolveIntent.CASHOUT) {
                BigDecimal win = current.getBetAmount()
                        .multiply(k)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                int piece = awardPuzzle(current, RoundStatus.CASHED_OUT, k);
                persistCashout(current, k, win, now, piece);
                current.setStatus(RoundStatus.CASHED_OUT);
                current.setCashoutMultiplier(k);
                current.setWinAmount(win);
                current.setEndedAt(now);
                current.setPuzzlePieceIndex(piece);
                log.info("Cashout OK: gameId={} win={} puzzlePiece={}",
                        current.getGameId(), win, piece);
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
        return new RoundView(session, RoundStatus.FLYING, k, line, zone, session.getPointsEarned());
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
        return new RoundView(session, session.getStatus(), display, line, zone, session.getPointsEarned());
    }

    /**
     * Awards points for newly passed lines and a one-shot booster bonus (I8: snapshot only).
     * Idempotent: repeat polls with the same line add 0.
     */
    private void applyScoring(GameSession session, int currentLine) {
        GameConfig snapshot = session.getConfigSnapshot();
        int delta = scoringService.pointsForNewLines(
                session.getLinesPassedSnapshot(), currentLine, snapshot);

        BoosterResult booster = boosterFrom(session);
        if (scoringService.isBoosterActivating(booster, currentLine, session.isBoostActivated())) {
            delta += scoringService.boosterBonus(booster, snapshot);
            session.setBoostActivated(true);
        }

        session.setPointsEarned(session.getPointsEarned() + delta);
        session.setLinesPassedSnapshot(Math.max(session.getLinesPassedSnapshot(), currentLine));
    }

    private static BoosterResult boosterFrom(GameSession session) {
        if (session.getBoostTier() == null || session.getBoostTriggerLine() == null) {
            return null;
        }
        double multiplier = session.getBoostMultiplier() != null
                ? session.getBoostMultiplier().doubleValue()
                : 1.0;
        return new BoosterResult(
                session.getBoostTier(),
                "booster",
                multiplier,
                session.getBoostTriggerLine());
    }

    private int awardPuzzle(GameSession session, RoundStatus terminalStatus, BigDecimal cashoutK) {
        if (session.getPuzzlePieceIndex() != null) {
            return session.getPuzzlePieceIndex();
        }
        return puzzleService.roll(session.getServerSeedHex(), terminalStatus, cashoutK)
                .orElseThrow(() -> new IllegalStateException(
                        "Puzzle roll empty for terminal status " + terminalStatus));
    }

    private void persistCrash(GameSession session, Instant endedAt, int puzzlePiece) {
        transactionTemplate.executeWithoutResult(status -> {
            GameRound round = gameRoundRepository.findById(session.getGameId())
                    .orElseThrow(GameException::gameNotFound);
            if (round.getStatus() != RoundStatus.FLYING) {
                return;
            }
            round.setStatus(RoundStatus.CRASHED);
            round.setEndedAt(endedAt);
            round.setPointsEarned(session.getPointsEarned());
            if (round.getPuzzlePieceIndex() == null) {
                round.setPuzzlePieceIndex(puzzlePiece);
            }
        });
    }

    private void persistCashout(GameSession session, BigDecimal multiplier,
                                BigDecimal win, Instant endedAt, int puzzlePiece) {
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
            round.setPointsEarned(session.getPointsEarned());
            if (round.getPuzzlePieceIndex() == null) {
                round.setPuzzlePieceIndex(puzzlePiece);
            }
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
