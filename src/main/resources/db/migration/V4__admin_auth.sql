-- =============================================================================
-- V4: Admin JWT auth (Spec 6 A1)
-- admin_user + admin_refresh_token. UUIDs generated in Java.
-- =============================================================================

CREATE TABLE admin_user (
    id            UUID         NOT NULL,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(128),
    enabled       BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at TIMESTAMPTZ,

    CONSTRAINT pk_admin_user PRIMARY KEY (id),
    CONSTRAINT uq_admin_user_username UNIQUE (username)
);

CREATE TABLE admin_refresh_token (
    id            UUID         NOT NULL,
    admin_user_id UUID         NOT NULL,
    token_hash    VARCHAR(64)  NOT NULL,
    expires_at    TIMESTAMPTZ  NOT NULL,
    revoked_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    user_agent    VARCHAR(256),
    ip            VARCHAR(64),

    CONSTRAINT pk_admin_refresh_token PRIMARY KEY (id),
    CONSTRAINT fk_admin_refresh_token_user
        FOREIGN KEY (admin_user_id) REFERENCES admin_user(id) ON DELETE CASCADE,
    CONSTRAINT uq_admin_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_admin_refresh_token_user_exp ON admin_refresh_token (admin_user_id, expires_at);
