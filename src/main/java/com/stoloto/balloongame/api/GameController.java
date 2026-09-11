package com.stoloto.balloongame.api;

import com.stoloto.balloongame.api.dto.BetRequest;
import com.stoloto.balloongame.api.dto.CashoutResponse;
import com.stoloto.balloongame.api.dto.GameStateResponse;
import com.stoloto.balloongame.api.dto.StartGameResponse;
import com.stoloto.balloongame.api.dto.VerifyResponse;
import com.stoloto.balloongame.service.GameOrchestrator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Game HTTP API. Business rules live in {@link GameOrchestrator} / lifecycle, not here.
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

    /**
     * GET /api/game/state/{gameId} — polling. Secrets only after terminal status (I1).
     */
    @GetMapping("/state/{gameId}")
    public ResponseEntity<GameStateResponse> state(
            @RequestHeader("X-Player-Id") String xPlayerId,
            @PathVariable UUID gameId) {
        return ResponseEntity.ok(orchestrator.state(gameId, xPlayerId));
    }

    /**
     * POST /api/game/cashout/{gameId} — credit win if still FLYING and K &lt; crashPoint.
     */
    @PostMapping("/cashout/{gameId}")
    public ResponseEntity<CashoutResponse> cashout(
            @RequestHeader("X-Player-Id") String xPlayerId,
            @PathVariable UUID gameId) {
        return ResponseEntity.ok(orchestrator.cashout(gameId, xPlayerId));
    }

    /**
     * GET /api/game/verify/{gameId} — PF reveal after CRASHED / CASHED_OUT / VOID.
     */
    @GetMapping("/verify/{gameId}")
    public ResponseEntity<VerifyResponse> verify(
            @RequestHeader("X-Player-Id") String xPlayerId,
            @PathVariable UUID gameId) {
        return ResponseEntity.ok(orchestrator.verify(gameId, xPlayerId));
    }
}
