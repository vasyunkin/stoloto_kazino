package com.stoloto.balloongame.api.dto;

/**
 * STOMP payload on {@code /topic/game/{gameId}} (S14).
 *
 * <p>{@code type}: {@code tick} | {@code crash} | {@code cashout} | {@code void} | {@code booster}
 * — always carries a {@link PublicGameState} (no PF secrets).
 */
public record WsGameEvent(
        String type,
        PublicGameState state
) {}
