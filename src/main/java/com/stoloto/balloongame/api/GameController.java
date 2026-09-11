package com.stoloto.balloongame.api;

import com.stoloto.balloongame.api.dto.BetRequest;
import com.stoloto.balloongame.api.dto.StartGameResponse;
import com.stoloto.balloongame.service.GameOrchestrator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Game HTTP API. S4 exposes start only — state / cashout are S5.
 */
@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
public class GameController {

    private final GameOrchestrator orchestrator;

    /**
     * POST /api/game/start
     *
     * {@code X-Player-Id} must equal {@code BetRequest.playerId} (otherwise 403).
     * Response never includes crashPoint or serverSeed (I1).
     */
    @PostMapping("/start")
    public ResponseEntity<StartGameResponse> start(
            @RequestHeader("X-Player-Id") String xPlayerId,
            @Valid @RequestBody BetRequest request) {
        return ResponseEntity.ok(orchestrator.start(request, xPlayerId));
    }
}
