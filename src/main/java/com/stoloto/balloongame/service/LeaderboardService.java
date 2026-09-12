package com.stoloto.balloongame.service;

import com.stoloto.balloongame.api.dto.LeaderboardEntryResponse;
import com.stoloto.balloongame.domain.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Public points leaderboard (S15). Aggregates {@code game_round.points_earned} — no PF secrets.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaderboardService {

    private final PlayerRepository playerRepository;

    public List<LeaderboardEntryResponse> leaderboard(Integer limit) {
        int size = GameHistoryService.clampLimit(limit);
        List<Object[]> rows = playerRepository.findLeaderboardByPoints(PageRequest.of(0, size));
        List<LeaderboardEntryResponse> out = new ArrayList<>(rows.size());
        int rank = 1;
        for (Object[] row : rows) {
            String externalId = (String) row[0];
            long total = ((Number) row[1]).longValue();
            out.add(new LeaderboardEntryResponse(rank++, externalId, total));
        }
        return out;
    }
}
