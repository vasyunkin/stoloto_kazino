package com.stoloto.balloongame.service;

import com.stoloto.balloongame.api.dto.GgrResponse;
import com.stoloto.balloongame.api.dto.LedgerMismatchResponse;
import com.stoloto.balloongame.api.dto.PlayerAdminResponse;
import com.stoloto.balloongame.api.dto.RoundAdminResponse;
import com.stoloto.balloongame.api.exception.GameException;
import com.stoloto.balloongame.domain.entity.GameRound;
import com.stoloto.balloongame.domain.entity.Player;
import com.stoloto.balloongame.domain.repository.GameRoundRepository;
import com.stoloto.balloongame.domain.repository.PlayerAccountRepository;
import com.stoloto.balloongame.domain.repository.PlayerRepository;
import com.stoloto.balloongame.domain.repository.WalletLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOpsService {

    private final PlayerRepository playerRepository;
    private final PlayerAccountRepository accountRepository;
    private final GameRoundRepository gameRoundRepository;
    private final WalletLedgerRepository walletLedgerRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public GgrResponse ggr(Instant from, Instant to) {
        Instant end = to == null ? clock.instant().plusSeconds(1) : to;
        Instant start = from == null ? Instant.EPOCH : from;
        List<Object[]> rows = gameRoundRepository.sumSettledStakesAndWins(start, end);
        BigDecimal stakes = BigDecimal.ZERO;
        BigDecimal wins = BigDecimal.ZERO;
        if (!rows.isEmpty() && rows.get(0) != null) {
            Object[] row = rows.get(0);
            stakes = toMoney(row[0]);
            wins = toMoney(row[1]);
        }
        return new GgrResponse(stakes, wins, stakes.subtract(wins));
    }

    @Transactional(readOnly = true)
    public List<PlayerAdminResponse> searchPlayers(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        return playerRepository.searchByExternalId(q, PageRequest.of(0, 20)).stream()
                .map(this::toPlayer)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoundAdminResponse round(UUID gameId) {
        GameRound round = gameRoundRepository.findByIdWithPlayer(gameId)
                .orElseThrow(GameException::gameNotFound);
        return new RoundAdminResponse(
                round.getId(),
                round.getPlayer().getExternalId(),
                round.getStatus(),
                round.getBetAmount(),
                round.getWinAmount(),
                round.getCrashPoint(),
                round.getCashoutMultiplier(),
                round.getServerSeed(),
                round.getServerSeedHash(),
                round.getSeedPrecommit(),
                round.getClientSeed(),
                round.getNonce(),
                round.getAlgorithmVersion(),
                round.getStartedAt(),
                round.getEndedAt());
    }

    @Transactional(readOnly = true)
    public List<LedgerMismatchResponse> mismatches() {
        return walletLedgerRepository.findBalanceMismatches().stream()
                .map(row -> new LedgerMismatchResponse(
                        String.valueOf(row[0]),
                        toMoney(row[1]),
                        toMoney(row[2])))
                .toList();
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    @Transactional(readOnly = true)
    public void auditBalances() {
        List<LedgerMismatchResponse> rows = mismatches();
        if (!rows.isEmpty()) {
            log.error("Wallet ledger mismatch for {} player(s): {}", rows.size(),
                    rows.stream().map(LedgerMismatchResponse::externalId).toList());
        }
    }

    private PlayerAdminResponse toPlayer(Player player) {
        return new PlayerAdminResponse(
                player.getExternalId(),
                player.getBalance(),
                player.getBoosterCharges(),
                accountRepository.existsByPlayer_ExternalId(player.getExternalId()),
                player.getCreatedAt());
    }

    private static BigDecimal toMoney(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }
}
