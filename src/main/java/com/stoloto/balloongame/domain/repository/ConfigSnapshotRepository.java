package com.stoloto.balloongame.domain.repository;

import com.stoloto.balloongame.domain.entity.ConfigSnapshotEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConfigSnapshotRepository extends JpaRepository<ConfigSnapshotEntity, UUID> {

    List<ConfigSnapshotEntity> findAllByOrderByAppliedAtDesc(Pageable pageable);
}
