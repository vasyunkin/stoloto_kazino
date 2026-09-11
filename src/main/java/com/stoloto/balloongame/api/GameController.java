package com.stoloto.balloongame.api;

import com.stoloto.balloongame.api.dto.BetRequest;
import com.stoloto.balloongame.api.dto.CashoutResponse;
import com.stoloto.balloongame.api.dto.GameStateResponse;
import com.stoloto.balloongame.api.dto.StartGameResponse;
import com.stoloto.balloongame.api.dto.VerifyResponse;
import com.stoloto.balloongame.service.GameOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Game", description = "Старт раунда, polling state, cashout, PF verify")
public class GameController {

    private final GameOrchestrator orchestrator;

    /**
     * POST /api/game/start
     *
     * {@code X-Player-Id} must equal {@code BetRequest.playerId} (otherwise 403).
     * Response never includes crashPoint or serverSeed (I1).
     */
    @Operation(summary = "Начать раунд: списать ставку, вернуть gameId и commitHash (без crashPoint)")
    @PostMapping("/start")
    public ResponseEntity<StartGameResponse> start(
            @Parameter(description = "Должен совпадать с playerId в теле", required = true)
            @RequestHeader("X-Player-Id") String xPlayerId,
            @Valid @RequestBody BetRequest request) {
        return ResponseEntity.ok(orchestrator.start(request, xPlayerId));
    }

    /**
     * GET /api/game/state/{gameId} — polling. Secrets only after terminal status (I1).
     */
    @Operation(summary = "Состояние раунда. Poll каждые 100–250 ms. Секреты только после CRASHED/CASHED_OUT")
    @GetMapping("/state/{gameId}")
    public ResponseEntity<GameStateResponse> state(
            @Parameter(description = "Владелец раунда", required = true)
            @RequestHeader("X-Player-Id") String xPlayerId,
            @PathVariable UUID gameId) {
        return ResponseEntity.ok(orchestrator.state(gameId, xPlayerId));
    }

    /**
     * POST /api/game/cashout/{gameId} — credit win if still FLYING and K &lt; crashPoint.
     */
    @Operation(summary = "Зафиксировать выигрыш, если шар ещё в полёте и K < crashPoint")
    @PostMapping("/cashout/{gameId}")
    public ResponseEntity<CashoutResponse> cashout(
            @Parameter(description = "Владелец раунда", required = true)
            @RequestHeader("X-Player-Id") String xPlayerId,
            @PathVariable UUID gameId) {
        return ResponseEntity.ok(orchestrator.cashout(gameId, xPlayerId));
    }

    /**
     * GET /api/game/verify/{gameId} — PF reveal after CRASHED / CASHED_OUT / VOID.
     */
    @Operation(summary = "Provably Fair: seed и crashPoint после конца раунда")
    @GetMapping("/verify/{gameId}")
    public ResponseEntity<VerifyResponse> verify(
            @Parameter(description = "Владелец раунда", required = true)
            @RequestHeader("X-Player-Id") String xPlayerId,
            @PathVariable UUID gameId) {
        return ResponseEntity.ok(orchestrator.verify(gameId, xPlayerId));
    }
}
