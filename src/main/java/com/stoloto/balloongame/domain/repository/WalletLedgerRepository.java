package com.stoloto.balloongame.domain.repository;

import com.stoloto.balloongame.domain.entity.WalletLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface WalletLedgerRepository extends JpaRepository<WalletLedger, Long> {

    List<WalletLedger> findAllByPlayerIdOrderByCreatedAtDesc(UUID playerId);

    /**
     * Invariant check: player balance must equal sum(ledger) for that player.
     * Used in tests and optionally in audit endpoint.
     */
    /**
     * Invariant check: player.balance must equal this sum (I5).
     * Used in tests and optionally in audit.
     * All credit types (WIN, REFUND, DEPOSIT) add; DEBIT_BET subtracts.
     */
    @Query("""
            SELECT COALESCE(SUM(
                CASE
                    WHEN l.type IN ('CREDIT_WIN', 'CREDIT_REFUND', 'CREDIT_DEPOSIT') THEN l.amount
                    WHEN l.type = 'DEBIT_BET'                                         THEN -l.amount
                    ELSE 0
                END
            ), 0)
            FROM WalletLedger l
            WHERE l.player.id = :playerId
            """)
    BigDecimal sumBalanceByPlayerId(@Param("playerId") UUID playerId);

    /** Count entries for a player — used in concurrent test assertions. */
    long countByPlayerId(UUID playerId);
}
