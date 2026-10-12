package com.stoloto.balloongame.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AlbumSyncRequest(
        @NotNull @Size(max = 24) List<@Valid AlbumPieceRequest> pieces
) {}
