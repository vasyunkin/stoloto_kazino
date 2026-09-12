package com.stoloto.balloongame.api.dto;

import java.math.BigDecimal;

/**
 * Response for POST /api/players/{externalId}/deposit
 */
public record DepositResponse(
        String externalId,
        BigDecimal depositedAmount,
        BigDecimal newBalance,
        int boosterCharges
) {}
