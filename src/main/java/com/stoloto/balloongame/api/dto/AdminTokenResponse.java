package com.stoloto.balloongame.api.dto;

public record AdminTokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        AdminProfileResponse admin
) {}
