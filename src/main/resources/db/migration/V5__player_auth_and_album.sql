-- Player accounts (email + password) and server-side puzzle album.
-- Guest players stay on `player.external_id` without a row here.

CREATE TABLE player_account (
    id            UUID         NOT NULL,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    player_id     UUID         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_player_account PRIMARY KEY (id),
    CONSTRAINT uq_player_account_email UNIQUE (email),
    CONSTRAINT uq_player_account_player UNIQUE (player_id),
    CONSTRAINT fk_player_account_player
        FOREIGN KEY (player_id) REFERENCES player(id)
);

CREATE TABLE player_refresh_token (
    id            UUID         NOT NULL,
    account_id    UUID         NOT NULL,
    token_hash    VARCHAR(64)  NOT NULL,
    expires_at    TIMESTAMPTZ  NOT NULL,
    revoked_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_player_refresh_token PRIMARY KEY (id),
    CONSTRAINT fk_player_refresh_token_account
        FOREIGN KEY (account_id) REFERENCES player_account(id) ON DELETE CASCADE,
    CONSTRAINT uq_player_refresh_token_hash UNIQUE (token_hash)
);

CREATE TABLE puzzle_progress (
    player_id  UUID        NOT NULL,
    slot       INTEGER     NOT NULL,
    game_id    UUID,
    earned_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_puzzle_progress PRIMARY KEY (player_id, slot),
    CONSTRAINT ck_puzzle_progress_slot CHECK (slot >= 0 AND slot < 24),
    CONSTRAINT fk_puzzle_progress_player
        FOREIGN KEY (player_id) REFERENCES player(id)
);
