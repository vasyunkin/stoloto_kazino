package com.stoloto.balloongame.api.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Business logic exception that carries an HTTP status and error code.
 * Caught by GlobalExceptionHandler and serialized as ErrorResponse.
 */
@Getter
public class GameException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;

    public GameException(ErrorCode errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    // ── Convenience factories ─────────────────────────────────────────────

    public static GameException insufficientBalance() {
        return new GameException(ErrorCode.INSUFFICIENT_BALANCE,
                "Insufficient balance for this bet", HttpStatus.PAYMENT_REQUIRED);
    }

    public static GameException gameNotFound() {
        return new GameException(ErrorCode.GAME_NOT_FOUND,
                "Game round not found", HttpStatus.NOT_FOUND);
    }

    public static GameException alreadyCrashed() {
        return new GameException(ErrorCode.ALREADY_CRASHED,
                "Balloon has already crashed", HttpStatus.CONFLICT);
    }

    public static GameException alreadyCashedOut() {
        return new GameException(ErrorCode.ALREADY_CASHED_OUT,
                "Cashout was already performed", HttpStatus.CONFLICT);
    }

    public static GameException invalidBet(String detail) {
        return new GameException(ErrorCode.INVALID_BET, detail, HttpStatus.BAD_REQUEST);
    }

    public static GameException roundNotTerminal() {
        return new GameException(ErrorCode.ROUND_NOT_TERMINAL,
                "Provably Fair data is available only after the round ends", HttpStatus.CONFLICT);
    }

    public static GameException roundExpired() {
        return new GameException(ErrorCode.ROUND_EXPIRED,
                "Round is no longer active", HttpStatus.GONE);
    }

    public static GameException configValidationFailed(String detail) {
        return new GameException(ErrorCode.CONFIG_VALIDATION_FAILED, detail, HttpStatus.BAD_REQUEST);
    }

    public static GameException playerNotFound() {
        return new GameException(ErrorCode.PLAYER_NOT_FOUND,
                "Player not found", HttpStatus.NOT_FOUND);
    }

    public static GameException depositNotAllowed() {
        return new GameException(ErrorCode.DEPOSIT_NOT_ALLOWED,
                "Deposit endpoint is disabled in this environment", HttpStatus.FORBIDDEN);
    }

    public static GameException forbidden() {
        return new GameException(ErrorCode.FORBIDDEN, "Access denied", HttpStatus.FORBIDDEN);
    }
}
