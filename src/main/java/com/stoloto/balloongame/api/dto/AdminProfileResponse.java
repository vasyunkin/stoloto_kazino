package com.stoloto.balloongame.api.dto;

import java.util.UUID;

public record AdminProfileResponse(
        UUID id,
        String username,
        String displayName
) {}
