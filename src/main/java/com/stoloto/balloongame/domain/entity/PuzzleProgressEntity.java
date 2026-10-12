package com.stoloto.balloongame.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "puzzle_progress")
@IdClass(PuzzleProgressEntity.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class PuzzleProgressEntity {

    @Id
    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Id
    @Column(name = "slot", nullable = false)
    private int slot;

    @Column(name = "game_id")
    private UUID gameId;

    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt = Instant.now();

    public static class Key implements Serializable {
        private UUID playerId;
        private int slot;

        public Key() {
        }

        public Key(UUID playerId, int slot) {
            this.playerId = playerId;
            this.slot = slot;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public int getSlot() {
            return slot;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return slot == key.slot && java.util.Objects.equals(playerId, key.playerId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(playerId, slot);
        }
    }
}
