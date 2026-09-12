package com.stoloto.balloongame.service;

import com.stoloto.balloongame.api.dto.HistoryItemResponse;
import com.stoloto.balloongame.domain.entity.RoundStatus;
import com.stoloto.balloongame.domain.repository.GameRoundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Read-only public round history (S11). Single-table query on {@code game_round}, no player JOIN.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameHistoryService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    private static final Set<RoundStatus> TERMINAL = EnumSet.of(
            RoundStatus.CRASHED,
            RoundStatus.CASHED_OUT,
            RoundStatus.VOID
    );

    private final GameRoundRepository gameRoundRepository;

    public List<HistoryItemResponse> history(Integer limit) {
        int size = clampLimit(limit);
        return gameRoundRepository
                .findTerminalHistory(TERMINAL, PageRequest.of(0, size))
                .stream()
                .map(HistoryItemResponse::from)
                .toList();
    }

    public static int clampLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
