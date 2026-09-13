package com.stoloto.balloongame.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ConfigHistoryItemResponse(
        UUID id,
        Instant appliedAt,
        String appliedBy,
        String payloadPreview
) {}
