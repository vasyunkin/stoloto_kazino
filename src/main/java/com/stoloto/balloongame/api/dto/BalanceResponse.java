package com.stoloto.balloongame.api.dto;

import java.math.BigDecimal;

/**
 * Response for GET /api/players/{externalId}/balance
 */
public record BalanceResponse(
        String externalId,
        BigDecimal balance
) {}
