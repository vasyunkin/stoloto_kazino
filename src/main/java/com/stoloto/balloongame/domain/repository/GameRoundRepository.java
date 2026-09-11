package com.stoloto.balloongame.domain.repository;

import com.stoloto.balloongame.domain.entity.GameRound;
import com.stoloto.balloongame.domain.entity.RoundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameRoundRepository extends JpaRepository<GameRound, UUID> {

    @Query("SELECT r.id FROM GameRound r WHERE r.status = :status")
    List<UUID> findIdsByStatus(@Param("status") RoundStatus status);

    @Query("SELECT r.player.externalId FROM GameRound r WHERE r.id = :id")
    Optional<String> findPlayerExternalIdById(@Param("id") UUID id);

    long countByPlayer_ExternalId(String externalId);

    /** Rebuild {@code GameSession} from DB (cache miss). Player must be loaded (I6). */
    @Query("SELECT r FROM GameRound r JOIN FETCH r.player WHERE r.id = :id")
    Optional<GameRound> findByIdWithPlayer(@Param("id") UUID id);

    /**
     * Used by state/cashout to verify round ownership before operations (I6).
     */
    @Query("SELECT r FROM GameRound r WHERE r.id = :id AND r.player.externalId = :externalId")
    Optional<GameRound> findByIdAndPlayerExternalId(@Param("id") UUID id,
                                                     @Param("externalId") String externalId);

    /**
     * Bulk update FLYING → VOID at startup for recovery (S7).
     * Uses enum parameters — not string literals — to be safe with @Enumerated(STRING).
     */
    @Modifying
    @Query("UPDATE GameRound r SET r.status = :newStatus WHERE r.status = :oldStatus")
    int markAllFlyingAsVoid(
            @Param("oldStatus") RoundStatus oldStatus,
            @Param("newStatus") RoundStatus newStatus
    );
}
