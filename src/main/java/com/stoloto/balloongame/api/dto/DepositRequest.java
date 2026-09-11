package com.stoloto.balloongame.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request body for POST /api/players/{externalId}/deposit
 */
public record DepositRequest(

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be > 0")
        BigDecimal amount
) {}
