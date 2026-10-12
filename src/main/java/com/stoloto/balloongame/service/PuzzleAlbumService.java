package com.stoloto.balloongame.service;

import com.stoloto.balloongame.api.dto.AlbumPieceRequest;
import com.stoloto.balloongame.api.dto.AlbumSlotResponse;
import com.stoloto.balloongame.domain.entity.Player;
import com.stoloto.balloongame.domain.entity.PuzzleProgressEntity;
import com.stoloto.balloongame.domain.repository.GameRoundRepository;
import com.stoloto.balloongame.domain.repository.PuzzleProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PuzzleAlbumService {

    private final PuzzleProgressRepository progressRepository;
    private final PlayerService playerService;
    private final GameRoundRepository gameRoundRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AlbumSlotResponse> list(String playerId) {
        Player player = playerService.getPlayerOrThrow(playerId);
        return progressRepository.findByPlayerIdOrderBySlotAsc(player.getId()).stream()
                .map(PuzzleAlbumService::toResponse)
                .toList();
    }

    /**
     * First write for a slot wins. Repeating the same slot does not move {@code earnedAt} or {@code gameId}.
     */
    @Transactional
    public List<AlbumSlotResponse> sync(String playerId, List<AlbumPieceRequest> pieces) {
        Player player = playerService.getPlayerOrThrow(playerId);
        if (pieces != null) {
            for (AlbumPieceRequest piece : pieces) {
                progressRepository.findByPlayerIdAndSlot(player.getId(), piece.slot())
                        .orElseGet(() -> insert(player.getId(), piece));
            }
        }
        return list(playerId);
    }

    private PuzzleProgressEntity insert(UUID playerId, AlbumPieceRequest piece) {
        PuzzleProgressEntity row = new PuzzleProgressEntity();
        row.setPlayerId(playerId);
        row.setSlot(piece.slot());
        UUID gameId = piece.gameId();
        if (gameId != null && !gameRoundRepository.existsById(gameId)) {
            gameId = null;
        }
        row.setGameId(gameId);
        row.setEarnedAt(clock.instant());
        return progressRepository.save(row);
    }

    private static AlbumSlotResponse toResponse(PuzzleProgressEntity row) {
        return new AlbumSlotResponse(row.getSlot(), row.getGameId(), row.getEarnedAt());
    }
}
