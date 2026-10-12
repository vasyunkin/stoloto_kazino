package com.stoloto.balloongame.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AlbumSlotResponse(
        int slot,
        UUID gameId,
        Instant earnedAt
) {}
