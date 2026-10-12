package com.stoloto.balloongame.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.UUID;

public record AlbumPieceRequest(
        @Min(0) @Max(23) int slot,
        UUID gameId
) {}
