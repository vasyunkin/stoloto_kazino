package com.stoloto.balloongame.domain.entity;

/**
 * Life-cycle states of a game round.
 * Stored as VARCHAR(16) in game_round.status.
 */
public enum RoundStatus {
    /** Balloon is in the air; crashPoint not yet revealed. */
    FLYING,
    /** Server detected K(t) >= crashPoint; bet lost. */
    CRASHED,
    /** Player cashed out before crash; win credited. */
    CASHED_OUT,
    /** Round voided on server restart (refund issued). */
    VOID
}
