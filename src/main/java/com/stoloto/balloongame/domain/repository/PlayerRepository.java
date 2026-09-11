package com.stoloto.balloongame.domain.repository;

import com.stoloto.balloongame.domain.entity.Player;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
