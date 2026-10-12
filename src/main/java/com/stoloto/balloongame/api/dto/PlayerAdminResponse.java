package com.stoloto.balloongame.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PlayerAdminResponse(
        String externalId,
        BigDecimal balance,
        int boosterCharges,
        boolean account,
        Instant createdAt
) {}
