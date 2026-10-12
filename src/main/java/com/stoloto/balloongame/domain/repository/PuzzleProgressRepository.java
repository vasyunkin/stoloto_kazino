package com.stoloto.balloongame.domain.repository;

import com.stoloto.balloongame.domain.entity.PuzzleProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PuzzleProgressRepository extends JpaRepository<PuzzleProgressEntity, PuzzleProgressEntity.Key> {

    List<PuzzleProgressEntity> findByPlayerIdOrderBySlotAsc(UUID playerId);

    Optional<PuzzleProgressEntity> findByPlayerIdAndSlot(UUID playerId, int slot);
}
