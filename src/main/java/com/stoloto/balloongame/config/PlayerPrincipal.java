package com.stoloto.balloongame.config;

/**
 * Authenticated game player. {@code playerId} is {@code player.external_id}, not the email.
 */
public record PlayerPrincipal(String playerId) {
}
