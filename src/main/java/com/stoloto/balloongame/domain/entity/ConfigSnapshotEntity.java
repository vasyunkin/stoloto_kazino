package com.stoloto.balloongame.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit trail for every PUT /api/admin/config call (S8).
 * payload stores serialized GameConfig JSON.
 */
@Entity
@Table(name = "config_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class ConfigSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /** Serialized GameConfig as JSON (stored in JSONB column). */
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt = Instant.now();

    @Column(name = "applied_by", nullable = false, length = 64)
    private String appliedBy = "system";

    public ConfigSnapshotEntity(String payload, String appliedBy) {
        this.payload = payload;
        this.appliedBy = appliedBy;
    }
}
