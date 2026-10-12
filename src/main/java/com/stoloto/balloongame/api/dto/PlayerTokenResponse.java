package com.stoloto.balloongame.api.dto;

public record PlayerTokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String playerId,
        String email
) {}
