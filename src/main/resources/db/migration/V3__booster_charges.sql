-- UX-01: finite booster inventory per player
ALTER TABLE player
    ADD COLUMN booster_charges INT NOT NULL DEFAULT 5;

ALTER TABLE player
    ADD CONSTRAINT chk_player_booster_charges_non_negative CHECK (booster_charges >= 0);
