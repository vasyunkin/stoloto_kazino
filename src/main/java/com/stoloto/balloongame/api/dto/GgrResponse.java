package com.stoloto.balloongame.api.dto;

import java.math.BigDecimal;

public record GgrResponse(
        BigDecimal stakes,
        BigDecimal wins,
        BigDecimal ggr
) {}
