package com.stoloto.balloongame.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stoloto.balloongame.api.dto.BetRequest;
import com.stoloto.balloongame.api.dto.BoosterStateDto;
import com.stoloto.balloongame.api.dto.CashoutResponse;
import com.stoloto.balloongame.api.dto.GameStateResponse;
import com.stoloto.balloongame.api.dto.PublicGameState;
import com.stoloto.balloongame.api.dto.StartGameResponse;
import com.stoloto.balloongame.api.dto.VerifyResponse;
import com.stoloto.balloongame.api.exception.GameException;
import com.stoloto.balloongame.config.GameConfig;
import com.stoloto.balloongame.domain.entity.GameRound;
import com.stoloto.balloongame.domain.entity.RoundStatus;
import com.stoloto.balloongame.domain.repository.GameRoundRepository;
import com.stoloto.balloongame.domain.repository.PlayerRepository;
import com.stoloto.balloongame.provablyfair.ProvablyFairService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates round start: snapshot config, draw PF/crash/booster, debit bet,
 * persist {@code game_round} (variant A — seed + crashPoint written immediately),
 * and facade for state/cashout which delegate to {@link RoundLifecycleService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameOrchestrator {

    private static final String BOOSTER_NONE = "NONE";

    private final ConfigService configService;
    private final ProvablyFairService provablyFairService;
    private final PlayerFairnessService playerFairnessService;
    private final CrashMathService crashMathService;
    private final BoosterService boosterService;
    private final PlayerRepository playerRepository;
    private final PlayerService playerService;
    private final GameRoundRepository gameRoundRepository;
    private final GameSessionCache sessionCache;
    private final RoundLifecycleService lifecycle;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Starts a round. Business rules live here, not in the controller.
     *
     * @param request   validated bet payload
     * @param xPlayerId value of {@code X-Player-Id} (must match {@code request.playerId})
     */
    @Transactional
    public StartGameResponse start(BetRequest request, String xPlayerId) {
        if (xPlayerId == null || !xPlayerId.equals(request.playerId())) {
            throw GameException.forbidden();
        }

        GameConfig snapshot = configService.getSnapshot();
        if (!snapshot.getAdmin().isAcceptingBets()) {
            throw GameException.betsClosed();
        }
        snapshot.applyTheme(request.balloonType());
        validateBet(request, snapshot);

        // FIRST DB call in this transaction must be the pessimistic lock (I5).
        var player = playerRepository.findByExternalIdForUpdate(request.playerId())
                .orElseThrow(GameException::playerNotFound);

        if (gameRoundRepository.countByPlayer_IdAndStatus(player.getId(), RoundStatus.FLYING) > 0) {
            throw GameException.roundInProgress();
        }

        if (player.getBalance().compareTo(request.betAmount()) < 0) {
            throw GameException.insufficientBalance();
        }

        playerFairnessService.arm(player);
        String storedClientSeed = player.getPfClientSeed() == null ? "" : player.getPfClientSeed();
        String clientSeed = request.clientSeed() == null || request.clientSeed().isBlank()
                ? storedClientSeed
                : request.clientSeed();
        String serverSeed = player.getPfServerSeed();
        String seedPrecommit = player.getPfServerSeedHash();
        long nonce = player.getPfNonce() + 1;

        boolean wantBooster = request.boosterPreference() != null
                && !BOOSTER_NONE.equalsIgnoreCase(request.boosterPreference());
        if (wantBooster) {
            playerService.consumeBoosterCharge(player);
        }

        String commit = provablyFairService.computeCommitHash(
                serverSeed, clientSeed, nonce, snapshot.getProvablyFair().getHashAlgorithm());
        BigDecimal crashPoint = crashMathService.drawCrashMultiplier(
                serverSeed, clientSeed, nonce, snapshot, provablyFairService.algorithmVersion());
        Optional<BoosterResult> booster = rollBooster(
                request.boosterPreference(), serverSeed, crashPoint, snapshot);

        Instant startedAt = clock.instant();
        GameRound round = new GameRound();
        round.setPlayer(player);
        round.setBetAmount(request.betAmount());
        round.setBalloonType(request.balloonType());
        round.setStatus(RoundStatus.FLYING);
        round.setCrashPoint(crashPoint);
        round.setServerSeed(serverSeed);
        round.setServerSeedHash(commit);
        round.setSeedPrecommit(seedPrecommit);
        round.setAlgorithmVersion(provablyFairService.algorithmVersion());
        round.setAutoCashoutMultiplier(request.autoCashoutMultiplier());
        round.setClientSeed(clientSeed);
        round.setNonce(nonce);
        round.setStartedAt(startedAt);
        round.setGameConfigSnapshot(toJson(snapshot));
        booster.ifPresent(b -> {
            round.setBoostTier(b.tier());
            round.setBoostTriggerLine(b.triggerLine());
        });

        // Persist round BEFORE debit so wallet_ledger.game_round_id FK is satisfied.
        GameRound saved = gameRoundRepository.saveAndFlush(round);
        playerService.debitBet(player, request.betAmount(), saved);
        player.setPfNonce(nonce);
        player.setPfClientSeed(clientSeed);
        playerFairnessService.rotate(player);

        GameSession session = GameSession.builder()
                .gameId(saved.getId())
                .playerId(player.getExternalId())
                .betAmount(request.betAmount())
                .balloonType(request.balloonType())
                .crashPoint(crashPoint)
                .startTime(startedAt)
                .serverSeedHex(serverSeed)
                .clientSeed(clientSeed)
                .nonce(nonce)
                .commitHash(commit)
                .autoCashoutMultiplier(request.autoCashoutMultiplier())
                .boostTier(booster.map(BoosterResult::tier).orElse(null))
                .boostTriggerLine(booster.map(BoosterResult::triggerLine).orElse(null))
                .boostMultiplier(booster.map(BoosterResult::multiplier).orElse(null))
                .configSnapshot(snapshot)
                .createdAt(startedAt)
                .status(RoundStatus.FLYING)
                .build();
        sessionCache.put(session);

        // I7: do not log serverSeed or crashPoint while the round is FLYING.
        log.info("Round started: gameId={} playerId={} bet={} commitHash={}",
                saved.getId(), player.getExternalId(), request.betAmount(), commit);

        return new StartGameResponse(
                saved.getId(),
                commit,
                seedPrecommit,
                startedAt,
                player.getPfServerSeedHash(),
                nonce);
    }

    /**
     * Polling snapshot. Not {@code @Transactional} — lifecycle opens its own write TX
     * under the round lock (must not join a read-only outer transaction).
     */
    public GameStateResponse state(UUID gameId, String xPlayerId) {
        GameSession session = requireOwnedSession(gameId, xPlayerId);
        RoundView view = lifecycle.resolve(session, ResolveIntent.STATE_ONLY);
        return toStateResponse(view);
    }

    /**
     * Ownership gate for STOMP SUBSCRIBE (I6). Does not advance lifecycle.
     */
    public void assertOwnedSession(UUID gameId, String xPlayerId) {
        requireOwnedSession(gameId, xPlayerId);
    }

    /**
     * Public projection for WebSocket ticks — same resolve path as REST state (I3), no PF secrets.
     */
    public PublicGameState resolvePublicState(UUID gameId, String xPlayerId) {
        GameSession session = requireOwnedSession(gameId, xPlayerId);
        RoundView view = lifecycle.resolve(session, ResolveIntent.STATE_ONLY);
        return toPublicState(view);
    }

    public CashoutResponse cashout(UUID gameId, String xPlayerId) {
        GameSession session = requireOwnedSession(gameId, xPlayerId);
        RoundView view = lifecycle.resolve(session, ResolveIntent.CASHOUT);
        GameSession done = view.session();
        return new CashoutResponse(
                done.getGameId(),
                done.getCashoutMultiplier(),
                done.getWinAmount(),
                view.pointsTotal(),
                done.getServerSeedHex(),
                done.getPuzzlePieceIndex());
    }

    /**
     * Provably Fair reveal. Reads PostgreSQL (not the TTL cache) so terminal data
     * survives eviction. Seed is returned only when status is terminal (I1).
     */
    public VerifyResponse verify(UUID gameId, String xPlayerId) {
        GameRound round = gameRoundRepository.findByIdWithPlayer(gameId)
                .orElseThrow(GameException::gameNotFound);
        if (!round.getPlayer().getExternalId().equals(xPlayerId)) {
            throw GameException.forbidden();
        }
        if (!round.getStatus().isTerminal()) {
            throw GameException.roundNotTerminal();
        }
        GameConfig snapshot = readSnapshot(round.getGameConfigSnapshot());
        String algorithm = round.getAlgorithmVersion() == null
                ? ProvablyFairService.ALGORITHM_V1
                : round.getAlgorithmVersion();
        return new VerifyResponse(
                round.getId(),
                round.getServerSeed(),
                round.getClientSeed(),
                round.getNonce(),
                round.getServerSeedHash(),
                round.getCrashPoint(),
                algorithm,
                round.getBoostTier(),
                round.getBoostTriggerLine(),
                lookupBoostMultiplier(snapshot, round.getBoostTier()),
                round.getSeedPrecommit(),
                snapshot == null ? null : BigDecimal.valueOf(snapshot.getMath().getHouseEdge()),
                snapshot == null ? null : snapshot.getAdmin().getMaxWinMultiplier(),
                snapshot == null ? null : snapshot.getMath().getMinCrashPoint(),
                snapshot == null ? null : BigDecimal.valueOf(snapshot.getMath().getInstantCrashRate()));
    }

    /**
     * Put a DB FLYING round back into the session cache so the settler can finish it
     * without a connected client. Throws if the snapshot cannot be read.
     */
    public Optional<GameSession> restoreFlying(UUID gameId) {
        Optional<GameSession> cached = sessionCache.get(gameId);
        if (cached.isPresent()) {
            return cached;
        }
        GameRound round = gameRoundRepository.findByIdWithPlayer(gameId).orElse(null);
        if (round == null || round.getStatus() != RoundStatus.FLYING) {
            return Optional.empty();
        }
        GameSession session = toSession(round);
        sessionCache.put(session);
        return Optional.of(session);
    }

    private GameSession requireOwnedSession(UUID gameId, String xPlayerId) {
        Optional<GameSession> cached = sessionCache.get(gameId);
        if (cached.isPresent()) {
            GameSession session = cached.get();
            if (!session.getPlayerId().equals(xPlayerId)) {
                throw GameException.forbidden();
            }
            return session;
        }
        GameRound round = gameRoundRepository.findByIdWithPlayer(gameId)
                .orElseThrow(GameException::gameNotFound);
        if (!round.getPlayer().getExternalId().equals(xPlayerId)) {
            throw GameException.forbidden();
        }
        if (round.getStatus() == RoundStatus.FLYING) {
            GameSession restored = toSession(round);
            sessionCache.put(restored);
            return restored;
        }
        if (round.getStatus() == RoundStatus.VOID) {
            throw GameException.roundExpired();
        }
        GameSession session = toSession(round);
        sessionCache.put(session);
        return session;
    }

    private GameSession toSession(GameRound round) {
        GameConfig snapshot = fromJson(round.getGameConfigSnapshot());
        GameSession session = GameSession.builder()
                .gameId(round.getId())
                .playerId(round.getPlayer().getExternalId())
                .betAmount(round.getBetAmount())
                .balloonType(round.getBalloonType())
                .crashPoint(round.getCrashPoint())
                .startTime(round.getStartedAt())
                .serverSeedHex(round.getServerSeed())
                .clientSeed(round.getClientSeed())
                .nonce(round.getNonce())
                .commitHash(round.getServerSeedHash())
                .autoCashoutMultiplier(round.getAutoCashoutMultiplier())
                .boostTier(round.getBoostTier())
                .boostTriggerLine(round.getBoostTriggerLine())
                .boostMultiplier(lookupBoostMultiplier(snapshot, round.getBoostTier()))
                .configSnapshot(snapshot)
                .createdAt(round.getStartedAt())
                .status(round.getStatus())
                .cashoutMultiplier(round.getCashoutMultiplier())
                .winAmount(round.getWinAmount())
                .endedAt(round.getEndedAt())
                .boostActivated(round.getBoostTriggerLine() != null
                        && round.getStatus().isTerminal())
                .pointsEarned(round.getPointsEarned())
                .linesPassedSnapshot(0)
                .puzzlePieceIndex(round.getPuzzlePieceIndex())
                .build();
        return session;
    }

    private GameStateResponse toStateResponse(RoundView view) {
        PublicGameState pub = toPublicState(view);
        GameSession session = view.session();
        boolean flying = view.status() == RoundStatus.FLYING;
        return new GameStateResponse(
                pub.gameId(),
                pub.status(),
                pub.multiplier(),
                pub.lineIndex(),
                pub.zone(),
                pub.pointsTotal(),
                pub.pointsDelta(),
                pub.booster(),
                flying ? null : session.getCrashPoint(),
                flying ? null : session.getServerSeedHex(),
                pub.winAmount(),
                pub.puzzlePieceIndex());
    }

    /**
     * Maps a resolve tick to the secret-free public model used by WebSocket (S14).
     */
    public PublicGameState toPublicState(RoundView view) {
        GameSession session = view.session();
        boolean flying = view.status() == RoundStatus.FLYING;
        return new PublicGameState(
                session.getGameId(),
                view.status(),
                view.multiplier(),
                view.lineIndex(),
                view.zone(),
                view.pointsTotal(),
                view.pointsDelta(),
                boosterDto(session),
                view.status() == RoundStatus.CASHED_OUT ? session.getWinAmount() : null,
                flying ? null : session.getPuzzlePieceIndex());
    }

    private BoosterStateDto boosterDto(GameSession session) {
        if (session.getBoostTier() == null) {
            return null;
        }
        if (!session.isBoostActivated()) {
            return new BoosterStateDto(true, null, null, null, null, false);
        }
        String name = "Booster";
        if (session.getConfigSnapshot().getBoosters() != null) {
            name = session.getConfigSnapshot().getBoosters().getTiers().stream()
                    .filter(t -> t.getTier() == session.getBoostTier())
                    .map(GameConfig.BoosterTier::getName)
                    .findFirst()
                    .orElse(name);
        }
        return new BoosterStateDto(
                true,
                session.getBoostTier(),
                name,
                session.getBoostMultiplier(),
                session.getBoostTriggerLine(),
                true);
    }

    private static BigDecimal lookupBoostMultiplier(GameConfig snapshot, Integer tier) {
        if (tier == null || snapshot == null || snapshot.getBoosters() == null) {
            return null;
        }
        return snapshot.getBoosters().getTiers().stream()
                .filter(t -> t.getTier() == tier)
                .map(GameConfig.BoosterTier::getMultiplier)
                .findFirst()
                .orElse(null);
    }

    private GameConfig fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("game_config_snapshot missing on game_round");
        }
        try {
            return objectMapper.readValue(json, GameConfig.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize config snapshot", e);
        }
    }

    /** Verify still reveals the seed when a voided round has an unreadable snapshot. */
    private GameConfig readSnapshot(String json) {
        try {
            return fromJson(json);
        } catch (IllegalStateException ex) {
            return null;
        }
    }

    private void validateBet(BetRequest request, GameConfig snapshot) {
        BigDecimal min = snapshot.getAdmin().getMinBetAmount();
        BigDecimal max = snapshot.getAdmin().getMaxBetAmount();
        BigDecimal bet = request.betAmount();
        if (bet.compareTo(min) < 0 || bet.compareTo(max) > 0) {
            throw GameException.invalidBet("betAmount must be between " + min + " and " + max);
        }
        String type = request.balloonType();
        if (!"STANDARD".equals(type) && !"LUCKY".equals(type)) {
            throw GameException.invalidBet("balloonType must be STANDARD or LUCKY");
        }
        if (request.clientSeed() != null && request.clientSeed().length() > 64) {
            throw GameException.invalidBet("clientSeed must be at most 64 characters");
        }
        if (request.autoCashoutMultiplier() != null) {
            BigDecimal auto = request.autoCashoutMultiplier();
            BigDecimal autoMin = snapshot.getMath().getMinCashoutMultiplier();
            BigDecimal autoMax = snapshot.getAdmin().getMaxWinMultiplier();
            if (autoMin != null && auto.compareTo(autoMin) < 0) {
                throw GameException.invalidBet("autoCashoutMultiplier must be at least " + autoMin);
            }
            if (autoMax != null && auto.compareTo(autoMax) > 0) {
                throw GameException.invalidBet("autoCashoutMultiplier must be at most " + autoMax);
            }
        }
    }

    private Optional<BoosterResult> rollBooster(String preference,
                                                String serverSeedHex,
                                                BigDecimal crashPoint,
                                                GameConfig snapshot) {
        if (BOOSTER_NONE.equalsIgnoreCase(preference)) {
            return Optional.empty();
        }
        return boosterService.rollForRound(serverSeedHex, crashPoint, snapshot);
    }

    private String toJson(GameConfig snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize config snapshot", e);
        }
    }
}
