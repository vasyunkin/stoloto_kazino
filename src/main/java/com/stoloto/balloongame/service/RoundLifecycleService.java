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
                current.setLastPointsDelta(0);
                return viewOfTerminal(current);
            }

            Instant now = clock.instant();
            BigDecimal k = gameClock.multiplier(current, now);
            int line = gameClock.lineIndex(current, now);
            BigDecimal crashPoint = current.getCrashPoint();
            boolean crashed = k.compareTo(crashPoint) >= 0;
            BigDecimal auto = current.getAutoCashoutMultiplier();
            boolean autoDue = intent != ResolveIntent.CASHOUT
                    && auto != null
                    && auto.compareTo(crashPoint) < 0
                    && k.compareTo(auto) >= 0;

            if (autoDue) {
                settleCashout(current, auto, now);
                return viewOfTerminal(current);
            }

            if (crashed) {
                settleCrash(current, now);
                if (intent == ResolveIntent.CASHOUT) {
                    throw GameException.alreadyCrashed();
                }
                return viewOfTerminal(current);
            }

            applyScoring(current, line);
            BigDecimal effectiveK = effectiveMultiplier(current, k);

            if (intent == ResolveIntent.CASHOUT) {
                BigDecimal minCashout = current.getConfigSnapshot().getMath().getMinCashoutMultiplier();
                if (minCashout != null && k.compareTo(minCashout) < 0) {
                    throw GameException.cashoutTooEarly(minCashout);
                }
                settleCashout(current, k, now);
                return viewOfTerminal(current);
            }

            return flyingView(current, effectiveK, line);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Payout multiplier cannot exceed the round's max win, including after a booster.
     */
    public static BigDecimal capPayout(GameSession session, BigDecimal multiplier) {
        BigDecimal cap = session.getConfigSnapshot().getAdmin().getMaxWinMultiplier();
        if (cap != null && multiplier.compareTo(cap) > 0) {
            return cap.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return multiplier;
    }

    private void settleCrash(GameSession current, Instant now) {
        GameConfig snap = current.getConfigSnapshot();
        int scoredLine = crashMath.lineForMultiplier(
                current.getCrashPoint().doubleValue(),
                snap.getMath().getGrowthRate(),
                snap.getAscentSpeedLinesPerSec());
        applyScoring(current, scoredLine);
        int piece = awardPuzzle(current, RoundStatus.CRASHED, null);
        persistCrash(current, now, piece);
        current.setStatus(RoundStatus.CRASHED);
        current.setEndedAt(now);
        current.setPuzzlePieceIndex(piece);
        log.info("Round crashed: gameId={} puzzlePiece={}", current.getGameId(), piece);
    }

    /**
     * Cash out at {@code rawK} (manual click uses the server K; auto uses the target, not the overshoot).
     * Scoring uses the line of that multiplier so a late tick does not award height past the cashout.
     */
    private void settleCashout(GameSession current, BigDecimal rawK, Instant now) {
        GameConfig snap = current.getConfigSnapshot();
        int scoredLine = crashMath.lineForMultiplier(
                rawK.doubleValue(),
                snap.getMath().getGrowthRate(),
                snap.getAscentSpeedLinesPerSec());
        applyScoring(current, scoredLine);
        BigDecimal paid = capPayout(current, effectiveMultiplier(current, rawK));
        BigDecimal win = current.getBetAmount().multiply(paid).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        int piece = awardPuzzle(current, RoundStatus.CASHED_OUT, paid);
        persistCashout(current, paid, win, now, piece);
        current.setStatus(RoundStatus.CASHED_OUT);
        current.setCashoutMultiplier(paid);
        current.setWinAmount(win);
        current.setEndedAt(now);
        current.setPuzzlePieceIndex(piece);
        log.info("Cashout OK: gameId={} win={} puzzlePiece={}", current.getGameId(), win, piece);
    }

    /**
     * After booster activation, payout multiplier is raw K × boost tier multiplier.
     * Crash detection always uses raw K (caller).
     */
    public static BigDecimal effectiveMultiplier(GameSession session, BigDecimal rawK) {
        if (!session.isBoostActivated() || session.getBoostMultiplier() == null) {
            return rawK;
        }
        return rawK.multiply(session.getBoostMultiplier()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private RoundView flyingView(GameSession session, BigDecimal k, int line) {
        GameConfigSnapshotLines lines = GameConfigSnapshotLines.from(session);
        String zone = crashMath.zoneFor(line, lines.green(), lines.red());
        return new RoundView(session, RoundStatus.FLYING, k, line, zone,
                session.getPointsEarned(), session.getLastPointsDelta());
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
        return new RoundView(session, session.getStatus(), display, line, zone,
                session.getPointsEarned(), session.getLastPointsDelta());
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

        session.setLastPointsDelta(delta);
        session.setPointsEarned(session.getPointsEarned() + delta);
        session.setLinesPassedSnapshot(Math.max(session.getLinesPassedSnapshot(), currentLine));
    }

    private static BoosterResult boosterFrom(GameSession session) {
        if (session.getBoostTier() == null || session.getBoostTriggerLine() == null) {
            return null;
        }
        BigDecimal multiplier = session.getBoostMultiplier() != null
                ? session.getBoostMultiplier()
                : BigDecimal.ONE;
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
