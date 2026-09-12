-- S12: puzzle piece awarded on CRASHED / CASHED_OUT (not VOID). No money side-effect.
ALTER TABLE game_round
    ADD COLUMN puzzle_piece_index INT;

ALTER TABLE game_round
    ADD CONSTRAINT chk_game_round_puzzle_piece_non_negative
        CHECK (puzzle_piece_index IS NULL OR puzzle_piece_index >= 0);
