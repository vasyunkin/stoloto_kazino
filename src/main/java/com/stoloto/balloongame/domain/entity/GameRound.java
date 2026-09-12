package com.stoloto.balloongame.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistent record of one game round.
 *
 * Security invariants (from Spec §I1, §I7):
 *  - serverSeed is stored from start but NEVER included in any API response while FLYING.
 *  - crashPoint is stored from start but NEVER exposed while FLYING.
 *  - @JsonIgnore on serverSeed is a safety-net; API DTOs never include it directly.
 */
@Entity
@Table(name = "game_round")
@Getter
@Setter
@NoArgsConstructor
public class GameRound {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private Player player;

    @Column(name = "bet_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal betAmount;

    @Column(name = "balloon_type", nullable = false, length = 32)
    private String balloonType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RoundStatus status = RoundStatus.FLYING;

    /**
     * Target crash multiplier (determined at start, secret until terminal).
     * Never expose this via API while status = FLYING.
     */
    @Column(name = "crash_point", nullable = false, precision = 10, scale = 4)
    private BigDecimal crashPoint;

    @Column(name = "cashout_multiplier", precision = 10, scale = 4)
    private BigDecimal cashoutMultiplier;

    @Column(name = "win_amount", precision = 19, scale = 4)
    private BigDecimal winAmount;

    @Column(name = "points_earned", nullable = false)
    private int pointsEarned = 0;

    /**
     * Raw server seed (bytes as hex string).
     * Written to DB immediately at start (variant A, Spec §5.1).
     * NEVER logged or serialized to JSON until terminal status.
     */
    @JsonIgnore
    @Column(name = "server_seed", nullable = false, columnDefinition = "TEXT")
    private String serverSeed;

    /** SHA-256 commit hash shown to client before the round starts (Provably Fair). */
    @Column(name = "server_seed_hash", nullable = false, length = 64)
    private String serverSeedHash;

    @Column(name = "client_seed", nullable = false, length = 64)
    private String clientSeed = "";

    @Column(name = "nonce", nullable = false)
    private long nonce;

    @Column(name = "boost_tier")
    private Integer boostTier;

    @Column(name = "boost_trigger_line")
    private Integer boostTriggerLine;

    /**
     * JSON snapshot of GameConfig at round start (I8).
     * Populated in S4; used by recovery job (S7) to restore configSnapshot.
     */
    @Column(name = "game_config_snapshot", columnDefinition = "TEXT")
    private String gameConfigSnapshot;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "ended_at")
    private Instant endedAt;

    /**
     * Puzzle fragment index awarded on CRASHED / CASHED_OUT (S12). Null while FLYING / VOID.
     * Not a wallet credit — no ledger impact (I5).
     */
    @Column(name = "puzzle_piece_index")
    private Integer puzzlePieceIndex;
}
