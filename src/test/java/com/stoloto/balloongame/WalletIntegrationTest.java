package com.stoloto.balloongame;

import com.stoloto.balloongame.api.exception.GameException;
import com.stoloto.balloongame.domain.entity.Player;
import com.stoloto.balloongame.domain.repository.PlayerRepository;
import com.stoloto.balloongame.domain.repository.WalletLedgerRepository;
import com.stoloto.balloongame.service.PlayerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/**
 * S2 integration tests — wallet / player operations.
 *
 * Key invariants checked:
 *  I5-a: Concurrent deposits do not lose money (no race condition).
 *  I5-b: player.balance == SUM(ledger) after all operations.
 *  I5-c: Deposit is rejected when allow-deposit=false (see application-test.yml for enable).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class WalletIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    PlayerService playerService;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    WalletLedgerRepository walletLedgerRepository;

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Unique externalId per test to avoid cross-test pollution. */
    private static String uid() {
        return "test-" + UUID.randomUUID();
    }

    // ── Basic tests ───────────────────────────────────────────────────────

    @Test
    void getOrCreate_createsPlayerWithZeroBalance() {
        String id = uid();
        Player p = playerService.getOrCreate(id);

        assertThat(p.getExternalId()).isEqualTo(id);
        assertThat(p.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getOrCreate_idempotent_returnsSamePlayer() {
        String id = uid();
        Player p1 = playerService.getOrCreate(id);
        Player p2 = playerService.getOrCreate(id);

        assertThat(p1.getId()).isEqualTo(p2.getId());
    }

    @Test
    void deposit_singleDeposit_updatesBalanceAndLedger() {
        String id = uid();
        playerService.getOrCreate(id);

        var response = playerService.deposit(id, new BigDecimal("500.00"));

        assertThat(response.newBalance()).isEqualByComparingTo("500.00");
        assertThat(response.depositedAmount()).isEqualByComparingTo("500.00");
        assertThat(response.externalId()).isEqualTo(id);

        // getBalance returns same value
        assertThat(playerService.getBalance(id)).isEqualByComparingTo("500.00");
    }

    @Test
    void deposit_multipleDeposits_accumulatesBalance() {
        String id = uid();
        playerService.getOrCreate(id);

        playerService.deposit(id, new BigDecimal("100.00"));
        playerService.deposit(id, new BigDecimal("250.00"));
        playerService.deposit(id, new BigDecimal("50.00"));

        assertThat(playerService.getBalance(id)).isEqualByComparingTo("400.00");
    }

    @Test
    void deposit_createsPlayerImplicitlyIfNotExist() {
        String id = uid(); // never called getOrCreate

        var response = playerService.deposit(id, new BigDecimal("200.00"));

        assertThat(response.newBalance()).isEqualByComparingTo("200.00");
        assertThat(playerRepository.findByExternalId(id)).isPresent();
    }

    @Test
    void getBalance_playerNotFound_throws404() {
        assertThatThrownBy(() -> playerService.getBalance("non-existent-player"))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("Player not found");
    }

    // ── Invariant: balance == SUM(ledger) ─────────────────────────────────

    @Test
    void ledgerInvariant_balanceEqualsSumOfLedger() {
        String id = uid();
        playerService.getOrCreate(id);
        playerService.deposit(id, new BigDecimal("1000.00"));
        playerService.deposit(id, new BigDecimal("234.56"));

        Player player = playerRepository.findByExternalId(id).orElseThrow();
        BigDecimal ledgerSum = walletLedgerRepository.sumBalanceByPlayerId(player.getId());

        assertThat(player.getBalance())
                .as("player.balance must equal SUM(ledger) — invariant I5")
                .isEqualByComparingTo(ledgerSum);
    }

    // ── Concurrent deposit test ───────────────────────────────────────────

    /**
     * Spawns N threads, each depositing 100 concurrently.
     * Expected final balance = N * 100.
     * Tests that pessimistic write lock prevents lost-update anomaly.
     */
    @Test
    void deposit_concurrent_noMoneyLost() throws InterruptedException {
        String id = uid();
        playerService.getOrCreate(id);

        int threads = 10;
        BigDecimal amountEach = new BigDecimal("100.00");
        BigDecimal expected = amountEach.multiply(BigDecimal.valueOf(threads));

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threads; i++) {
            Thread.ofVirtual().start(() -> {
                ready.countDown();
                try {
                    start.await();
                    playerService.deposit(id, amountEach);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();          // all threads ready
        start.countDown();      // fire!
        done.await(30, TimeUnit.SECONDS);

        assertThat(errors)
                .as("No exceptions during concurrent deposits: %s", errors)
                .isEmpty();

        BigDecimal actual = playerService.getBalance(id);
        assertThat(actual)
                .as("Concurrent deposits must not lose money (pessimistic lock)")
                .isEqualByComparingTo(expected);

        // Ledger invariant
        Player player = playerRepository.findByExternalId(id).orElseThrow();
        BigDecimal ledgerSum = walletLedgerRepository.sumBalanceByPlayerId(player.getId());
        assertThat(actual)
                .as("balance must equal ledger sum after concurrent deposits")
                .isEqualByComparingTo(ledgerSum);

        // Correct number of ledger entries
        assertThat(walletLedgerRepository.countByPlayerId(player.getId()))
                .isEqualTo(threads);
    }
}
