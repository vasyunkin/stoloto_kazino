package com.stoloto.balloongame.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Append-only ledger entry.
 * Every balance change (bet debit, win credit, refund) has exactly one entry.
 * Balance integrity: player.balance == sum of ledger entries for that player (I5).
 */
@Entity
@Table(name = "wallet_ledger")
@Getter
@Setter
@NoArgsConstructor
public class WalletLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private Player player;

    /** Nullable — e.g. manual deposit in dev has no game round. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_round_id", updatable = false)
    private GameRound gameRound;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private LedgerType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public WalletLedger(Player player, GameRound gameRound, LedgerType type, BigDecimal amount) {
        this.player = player;
        this.gameRound = gameRound;
        this.type = type;
        this.amount = amount;
    }
}
