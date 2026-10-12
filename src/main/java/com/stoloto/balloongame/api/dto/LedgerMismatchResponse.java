package com.stoloto.balloongame.api.dto;

import java.math.BigDecimal;

public record LedgerMismatchResponse(
        String externalId,
        BigDecimal balance,
        BigDecimal ledgerBalance
) {}
