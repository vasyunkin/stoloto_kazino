package com.stoloto.balloongame.api;

import com.stoloto.balloongame.api.dto.BalanceResponse;
import com.stoloto.balloongame.api.dto.DepositRequest;
import com.stoloto.balloongame.api.dto.DepositResponse;
import com.stoloto.balloongame.api.dto.LeaderboardEntryResponse;
import com.stoloto.balloongame.service.LeaderboardService;
import com.stoloto.balloongame.service.PlayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Wallet API — get balance and deposit funds.
 *
 * Deposit is intentionally unprotected by admin key (it is guarded by the
 * game.admin.allow-deposit config flag instead). In a real deployment, this
 * flag is set to false and deposits go through a payment gateway.
 */
@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
@Tag(name = "Players", description = "Кошелёк: баланс и демо-пополнение")
public class PlayerController {

    private final PlayerService playerService;
    private final LeaderboardService leaderboardService;

    /**
     * GET /api/players/leaderboard
     *
     * Public ranking by sum of round points. No secrets / balances.
     */
    @Operation(summary = "Рейтинг по сумме очков за раунды (S15). Без балансов и PF-секретов")
    @GetMapping("/leaderboard")
    public ResponseEntity<List<LeaderboardEntryResponse>> leaderboard(
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(leaderboardService.leaderboard(limit));
    }

    /**
     * GET /api/players/{externalId}/balance
     *
     * Returns the current wallet balance for the given player.
     * If the player does not exist, returns 404 PLAYER_NOT_FOUND.
     */
    @Operation(summary = "Текущий баланс. 404 PLAYER_NOT_FOUND, если игрока ещё не было")
    @GetMapping("/{externalId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable String externalId) {
        return ResponseEntity.ok(playerService.getWallet(externalId));
    }

    /**
     * POST /api/players/{externalId}/deposit
     *
     * Adds funds to the player wallet. Creates the player if they don't exist yet.
     * Guarded by game.admin.allow-deposit (disabled in prod by default).
     *
     * Body: { "amount": 500.00 }
     * Response 200: { "externalId", "depositedAmount", "newBalance" }
     * Response 403: DEPOSIT_NOT_ALLOWED if the flag is false.
     */
    @Operation(summary = "Демо-пополнение (создаёт игрока). 403, если allow-deposit=false")
    @PostMapping("/{externalId}/deposit")
    public ResponseEntity<DepositResponse> deposit(
            @PathVariable String externalId,
            @Valid @RequestBody DepositRequest request) {

        DepositResponse response = playerService.deposit(externalId, request.amount());
        return ResponseEntity.ok(response);
    }
}
