package com.stoloto.balloongame.api.dto;

/**
 * One row of {@code GET /api/players/leaderboard} (S15). No wallet / PF secrets.
 *
 * @param rank         1-based position in the current page (by total points desc)
 * @param externalId   player external id
 * @param totalPoints  sum of {@code game_round.points_earned}
 */
public record LeaderboardEntryResponse(
        int rank,
        String externalId,
        long totalPoints
) {}
