package com.stoloto.balloongame.domain.repository;

import com.stoloto.balloongame.domain.entity.Player;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

    Optional<Player> findByExternalId(String externalId);

    /**
     * Pessimistic write lock on a single player row.
     * Used by PlayerService before any balance mutation (I5).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Player p WHERE p.id = :id")
    Optional<Player> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Pessimistic write lock by externalId — used at round start and deposit.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Player p WHERE p.externalId = :externalId")
    Optional<Player> findByExternalIdForUpdate(@Param("externalId") String externalId);

    /**
     * Leaderboard (S15): sum of round points per player, highest first. No secrets.
     */
    @Query("""
            SELECT p.externalId, COALESCE(SUM(r.pointsEarned), 0)
            FROM GameRound r JOIN r.player p
            GROUP BY p.externalId
            ORDER BY SUM(r.pointsEarned) DESC, p.externalId ASC
            """)
    List<Object[]> findLeaderboardByPoints(Pageable pageable);
}
