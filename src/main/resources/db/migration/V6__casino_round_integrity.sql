-- Casino crash integrity: per-player provably-fair chain, payout target, algorithm version.

ALTER TABLE player
    ADD COLUMN pf_server_seed TEXT,
    ADD COLUMN pf_server_seed_hash VARCHAR(64),
    ADD COLUMN pf_nonce BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN pf_client_seed VARCHAR(64) NOT NULL DEFAULT '';

ALTER TABLE game_round
    ADD COLUMN algorithm_version VARCHAR(16) NOT NULL DEFAULT 'crash-v1',
    ADD COLUMN auto_cashout_multiplier NUMERIC(10, 4),
    ADD COLUMN seed_precommit VARCHAR(64);
