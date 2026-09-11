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
    CONFIG_VALIDATION_FAILED,
    FORBIDDEN,
    VALIDATION_ERROR,
    INTERNAL_ERROR
}
