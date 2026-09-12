package com.stoloto.balloongame.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A registered player with a wallet balance.
 *
 * Balance is modified ONLY via WalletLedger entries under pessimistic write lock (I5).
 * Never mutate balance directly outside PlayerService.
 */
@Entity
@Table(name = "player")
@Getter
@Setter
@NoArgsConstructor
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /** Public/external identifier supplied by the client (e.g. username or UUID from auth). */
    @Column(name = "external_id", nullable = false, unique = true, length = 64)
    private String externalId;

    /**
     * Current wallet balance. Always >= 0 (enforced by DB CHECK constraint).
     * Modified only in PlayerService under PESSIMISTIC_WRITE lock.
     */
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    /**
     * Remaining AUTO booster rolls. Consumed on start when preference is AUTO.
     * Replenished on demo deposit (+1, capped).
     */
    @Column(name = "booster_charges", nullable = false)
    private int boosterCharges = 5;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Player(String externalId) {
        this.externalId = externalId;
    }
}
