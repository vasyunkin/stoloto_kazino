package com.stoloto.balloongame.api.dto;

/**
 * Hash of the server seed that the next {@code POST /start} will consume.
 * Fetch this before choosing a client seed.
 */
public record FairnessResponse(
        String serverSeedHash,
        long nextNonce,
        String clientSeed
) {}
