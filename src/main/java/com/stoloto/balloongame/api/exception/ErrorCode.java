package com.stoloto.balloongame.api.exception;

/**
 * Application error codes used in ErrorResponse.
 * Stable strings — frontEnd maps them to locale-specific messages.
 */
public enum ErrorCode {
    INSUFFICIENT_BALANCE,
    GAME_NOT_FOUND,
    PLAYER_NOT_FOUND,
    ALREADY_CASHED_OUT,
    ALREADY_CRASHED,
    INVALID_BET,
    DEPOSIT_NOT_ALLOWED,     // game.admin.allow-deposit=false
    ROUND_NOT_TERMINAL,      // verify called before round ended
    ROUND_EXPIRED,           // FLYING cache miss / recovered VOID — 410
    CONFIG_VALIDATION_FAILED,
    FORBIDDEN,
    VALIDATION_ERROR,
    RATE_LIMITED,            // 429 — start/cashout spam (optional filter)
    CASHOUT_TOO_EARLY,       // 409 — K below minCashoutMultiplier
    NO_BOOSTER_CHARGES,      // 400 — AUTO requested with zero charges
    INTERNAL_ERROR
}
