package com.stoloto.balloongame.domain.repository;

import com.stoloto.balloongame.domain.entity.ConfigSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConfigSnapshotRepository extends JpaRepository<ConfigSnapshotEntity, UUID> {
}
