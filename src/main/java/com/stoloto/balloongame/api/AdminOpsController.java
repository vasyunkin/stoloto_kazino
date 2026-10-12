package com.stoloto.balloongame.api;

import com.stoloto.balloongame.api.dto.GgrResponse;
import com.stoloto.balloongame.api.dto.LedgerMismatchResponse;
import com.stoloto.balloongame.api.dto.PlayerAdminResponse;
import com.stoloto.balloongame.api.dto.RoundAdminResponse;
import com.stoloto.balloongame.service.AdminOpsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin ops", description = "Стоп ставок — в конфиге admin.acceptingBets. Здесь поиск, GGR и сверка.")
@SecurityRequirement(name = "Bearer")
public class AdminOpsController {

    private final AdminOpsService adminOpsService;

    @Operation(summary = "Оборот и GGR по завершённым раундам (ставки минус выплаты). VOID не входит.")
    @GetMapping("/stats")
    public GgrResponse stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return adminOpsService.ggr(from, to);
    }

    @Operation(summary = "Поиск игрока по части external id")
    @GetMapping("/players")
    public List<PlayerAdminResponse> players(@RequestParam(name = "q", defaultValue = "") String query) {
        return adminOpsService.searchPlayers(query);
    }

    @Operation(summary = "Карточка раунда, включая seed")
    @GetMapping("/rounds/{gameId}")
    public RoundAdminResponse round(@PathVariable UUID gameId) {
        return adminOpsService.round(gameId);
    }

    @Operation(summary = "Игроки, у которых баланс не сходится с журналом")
    @GetMapping("/reconciliation")
    public List<LedgerMismatchResponse> reconciliation() {
        return adminOpsService.mismatches();
    }
}
