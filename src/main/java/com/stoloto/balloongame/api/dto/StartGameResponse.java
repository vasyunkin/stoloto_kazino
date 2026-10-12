package com.stoloto.balloongame.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response of {@code POST /api/game/start}.
 *
 * <p>I1: must never contain {@code crashPoint} or {@code serverSeed}.
 * {@code serverSeedHash} is SHA-256 of the server seed hex committed before this bet.
 * {@code commitHash} is SHA-256 of {@code serverSeed|clientSeed|nonce}.
 */
public record StartGameResponse(
        UUID gameId,
        String commitHash,
        String serverSeedHash,
        Instant startedAt,
        String nextServerSeedHash,
        long nonce
) {}
