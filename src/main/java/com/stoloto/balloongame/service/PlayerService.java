package com.stoloto.balloongame.service;

import com.stoloto.balloongame.api.dto.BalanceResponse;
import com.stoloto.balloongame.api.dto.DepositResponse;
import com.stoloto.balloongame.api.exception.GameException;
import com.stoloto.balloongame.domain.entity.LedgerType;
import com.stoloto.balloongame.domain.entity.Player;
import com.stoloto.balloongame.domain.entity.WalletLedger;
import com.stoloto.balloongame.domain.repository.PlayerRepository;
import com.stoloto.balloongame.domain.repository.WalletLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Wallet operations for a player.
 *
 * Money rules (I5):
 *  - Every balance change goes through wallet_ledger.
 *  - Balance mutations always hold PESSIMISTIC_WRITE lock on the player row.
 *  - player.balance == SUM(ledger) at all times (invariant verifiable by sumBalanceByPlayerId).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final WalletLedgerRepository walletLedgerRepository;
    private final ConfigService configService;
    /** Proxy for @Transactional self-invocation (deposit guard runs outside write-TX). */
    private final ObjectProvider<PlayerService> self;

    // ── Read operations ───────────────────────────────────────────────────

    /**
     * Get current balance. Read-only, no lock required.
     */
    public BigDecimal getBalance(String externalId) {
        return getPlayerOrThrow(externalId).getBalance();
    }

    /** Wallet snapshot for balance API (balance + booster charges). */
    public BalanceResponse getWallet(String externalId) {
        Player player = getPlayerOrThrow(externalId);
        return new BalanceResponse(externalId, player.getBalance(), player.getBoosterCharges());
    }

    public static final int MAX_BOOSTER_CHARGES = 20;

    /**
     * Fetch player or throw PLAYER_NOT_FOUND (404).
     * Used by game services (S4+) to look up players.
     */
    public Player getPlayerOrThrow(String externalId) {
        return playerRepository.findByExternalId(externalId)
                .orElseThrow(GameException::playerNotFound);
    }

    // ── Write operations ──────────────────────────────────────────────────

    /**
     * Get existing player or create a new one (zero balance).
     *
     * <p>Not fully safe under extreme concurrent first-creation races, but acceptable
     * for the hackathon scope. The unique constraint on external_id ensures the DB
     * stays consistent even if two threads race here.
     */
    @Transactional
    public Player getOrCreate(String externalId) {
        return playerRepository.findByExternalId(externalId)
                .orElseGet(() -> {
                    log.info("Creating new player: externalId={}", externalId);
                    return playerRepository.save(new Player(externalId));
                });
    }

    /**
     * Deposit funds into player wallet.
     *
     * <p>Guards run <strong>outside</strong> the write transaction (S10 / Spec 2):
     * <ul>
     *   <li>{@code ConfigService} snapshot {@code admin.allowDeposit} must be true</li>
     *   <li>amount &gt; 0</li>
     * </ul>
     * Reject path must not acquire a player lock or write ledger.
     *
     * <p>Flow (I5):
     * <ol>
     *   <li>Validate allow-deposit flag (runtime snapshot) and amount.</li>
     *   <li>Write-TX: lock player → credit balance → {@code CREDIT_DEPOSIT} ledger.</li>
     * </ol>
     *
     * @param externalId player identifier
     * @param amount     positive deposit amount
     * @return DepositResponse with new balance
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DepositResponse deposit(String externalId, BigDecimal amount) {
        assertDepositAllowed();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw GameException.invalidBet("Deposit amount must be positive");
        }
        return self.getObject().depositInTransaction(externalId, amount);
    }

    /**
     * Money write path for deposit. First DB call must be {@code FOR UPDATE} (I5).
     */
    @Transactional
    public DepositResponse depositInTransaction(String externalId, BigDecimal amount) {
        // Defense in depth if admin flipped the flag between guard and TX entry.
        assertDepositAllowed();

        // Acquire PESSIMISTIC_WRITE lock as the FIRST DB call in this transaction.
        // Must NOT call findByExternalId() before this — that would put a stale entity into
        // Hibernate L1 cache, and findByExternalIdForUpdate would then return the cached
        // (stale) balance instead of re-reading fresh committed data from PostgreSQL.
        Player player = playerRepository.findByExternalIdForUpdate(externalId)
                .orElseGet(() -> playerRepository.saveAndFlush(new Player(externalId)));

        BigDecimal newBalance = player.getBalance().add(amount);
        player.setBalance(newBalance);
        int charges = Math.min(MAX_BOOSTER_CHARGES, player.getBoosterCharges() + 1);
        player.setBoosterCharges(charges);

        walletLedgerRepository.save(new WalletLedger(player, null, LedgerType.CREDIT_DEPOSIT, amount));

        log.info("Deposit OK: player={} amount={} newBalance={} boosterCharges={}",
                externalId, amount, newBalance, charges);
        return new DepositResponse(externalId, amount, newBalance, charges);
    }

    /**
     * Consume one AUTO booster charge. Caller must hold player FOR UPDATE.
     */
    public void consumeBoosterCharge(Player player) {
        if (player.getBoosterCharges() <= 0) {
            throw GameException.noBoosterCharges();
        }
        player.setBoosterCharges(player.getBoosterCharges() - 1);
    }

    private void assertDepositAllowed() {
        if (!configService.getSnapshot().getAdmin().isAllowDeposit()) {
            throw GameException.depositNotAllowed();
        }
    }

    /**
     * Debit bet amount from player balance.
     * Called from GameOrchestrator.start() (S4) — always inside an outer transaction with lock.
     *
     * <p>Precondition: caller must hold PESSIMISTIC_WRITE lock on the player row.
     *
     * @param player    locked player entity
     * @param amount    positive bet amount
     * @param gameRound the game round being started (for ledger FK)
     */
    @Transactional
    public void debitBet(Player player,
                         BigDecimal amount,
                         com.stoloto.balloongame.domain.entity.GameRound gameRound) {
        if (player.getBalance().compareTo(amount) < 0) {
            throw GameException.insufficientBalance();
        }
        player.setBalance(player.getBalance().subtract(amount));
        walletLedgerRepository.save(new WalletLedger(player, gameRound, LedgerType.DEBIT_BET, amount));
    }

    /**
     * Credit win amount to player balance.
     * Called from RoundLifecycleService.cashout() (S5) — inside transaction with lock.
     *
     * <p>Precondition: caller must hold PESSIMISTIC_WRITE lock on the player row.
     *
     * @param player    locked player entity
     * @param amount    positive win amount
     * @param gameRound the finished game round
     */
    @Transactional
    public void creditWin(Player player,
                          BigDecimal amount,
                          com.stoloto.balloongame.domain.entity.GameRound gameRound) {
        player.setBalance(player.getBalance().add(amount));
        walletLedgerRepository.save(new WalletLedger(player, gameRound, LedgerType.CREDIT_WIN, amount));
    }

    /**
     * Refund bet to player (used in startup recovery for VOID rounds — S7).
     * Precondition: caller must hold PESSIMISTIC_WRITE lock on the player row.
     */
    @Transactional
    public void refundBet(Player player,
                          BigDecimal amount,
                          com.stoloto.balloongame.domain.entity.GameRound gameRound) {
        player.setBalance(player.getBalance().add(amount));
        walletLedgerRepository.save(new WalletLedger(player, gameRound, LedgerType.CREDIT_REFUND, amount));
    }
}
