package com.stoloto.balloongame.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response of {@code POST /api/game/start}.
 *
 * <p>I1: must never contain {@code crashPoint} or {@code serverSeed}.
 * {@code serverSeedHash} is an alias of {@code commitHash} for the UI PF panel.
 */
public record StartGameResponse(
        UUID gameId,
        String commitHash,
        String serverSeedHash,
        Instant startedAt
) {}
