-- =============================================================================
-- V1: Full schema for balloon-game (Crash Game Backend)
-- All tables created in one migration to avoid partial-audit risks.
-- UUIDs are generated in Java (@GeneratedValue); no pgcrypto extension needed.
-- =============================================================================

-- ─── Player ──────────────────────────────────────────────────────────────────
CREATE TABLE player (
    id          UUID         NOT NULL,
    external_id VARCHAR(64)  NOT NULL,
    balance     NUMERIC(19,4) NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_player PRIMARY KEY (id),
    CONSTRAINT uq_player_external_id UNIQUE (external_id),
    CONSTRAINT chk_player_balance_non_negative CHECK (balance >= 0)
);

-- ─── Game Round ──────────────────────────────────────────────────────────────
-- status values: FLYING | CRASHED | CASHED_OUT | VOID
-- server_seed stored immediately at start (Spec §5.1, variant A).
-- @JsonIgnore on entity field; never exposed while FLYING.
CREATE TABLE game_round (
    id                   UUID          NOT NULL,
    player_id            UUID          NOT NULL,
    bet_amount           NUMERIC(19,4) NOT NULL,
    balloon_type         VARCHAR(32)   NOT NULL,
    status               VARCHAR(16)   NOT NULL DEFAULT 'FLYING',
    crash_point          NUMERIC(10,4) NOT NULL,
    cashout_multiplier   NUMERIC(10,4),
    win_amount           NUMERIC(19,4),
    points_earned        INT           NOT NULL DEFAULT 0,
    server_seed          TEXT          NOT NULL,  -- never exposed while FLYING
    server_seed_hash     VARCHAR(64)   NOT NULL,  -- commitHash shown to client
    client_seed          VARCHAR(64)   NOT NULL DEFAULT '',
    nonce                BIGINT        NOT NULL,
    boost_tier           INT,
    boost_trigger_line   INT,
    game_config_snapshot TEXT,                    -- JSON snapshot of GameConfig at start (S4)
    started_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    ended_at             TIMESTAMPTZ,

    CONSTRAINT pk_game_round PRIMARY KEY (id),
    CONSTRAINT fk_game_round_player FOREIGN KEY (player_id) REFERENCES player(id),
    CONSTRAINT chk_game_round_status
        CHECK (status IN ('FLYING', 'CRASHED', 'CASHED_OUT', 'VOID')),
    CONSTRAINT chk_game_round_bet_positive CHECK (bet_amount > 0),
    CONSTRAINT chk_game_round_crash_point_gte_1 CHECK (crash_point >= 1.0)
);

CREATE INDEX idx_game_round_player_started ON game_round (player_id, started_at DESC);
CREATE INDEX idx_game_round_status           ON game_round (status);

-- ─── Wallet Ledger (append-only) ─────────────────────────────────────────────
-- type values: DEBIT_BET | CREDIT_WIN | CREDIT_REFUND | CREDIT_DEPOSIT
CREATE TABLE wallet_ledger (
    id            BIGSERIAL     NOT NULL,
    player_id     UUID          NOT NULL,
    game_round_id UUID,
    type          VARCHAR(20)   NOT NULL,
    amount        NUMERIC(19,4) NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_wallet_ledger PRIMARY KEY (id),
    CONSTRAINT fk_ledger_player     FOREIGN KEY (player_id)     REFERENCES player(id),
    CONSTRAINT fk_ledger_game_round FOREIGN KEY (game_round_id) REFERENCES game_round(id),
    CONSTRAINT chk_ledger_type
        CHECK (type IN ('DEBIT_BET', 'CREDIT_WIN', 'CREDIT_REFUND', 'CREDIT_DEPOSIT')),
    CONSTRAINT chk_ledger_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_wallet_ledger_player ON wallet_ledger (player_id, created_at DESC);

-- ─── Config Snapshot (admin PUT /api/admin/config history) ───────────────────
CREATE TABLE config_snapshot (
    id         UUID        NOT NULL,
    payload    JSONB       NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    applied_by VARCHAR(64) NOT NULL DEFAULT 'system',

    CONSTRAINT pk_config_snapshot PRIMARY KEY (id)
);
