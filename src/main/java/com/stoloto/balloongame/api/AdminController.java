package com.stoloto.balloongame.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.stoloto.balloongame.api.dto.ConfigHistoryItemResponse;
import com.stoloto.balloongame.api.exception.GameException;
import com.stoloto.balloongame.config.AdminPrincipal;
import com.stoloto.balloongame.config.GameConfig;
import com.stoloto.balloongame.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Runtime config. JWT Bearer or legacy {@code X-Admin-Key} (dual until cutover).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Authorize → Bearer JWT (login) или deprecated AdminKey.")
@SecurityRequirement(name = "Bearer")
@SecurityRequirement(name = "AdminKey")
public class AdminController {

    private final ConfigService configService;

    @Operation(summary = "Текущий runtime-снимок конфига (без apiKey / jwt secrets)")
    @GetMapping("/config")
    public ResponseEntity<GameConfig> getConfig() {
        return ResponseEntity.ok(configService.getSnapshot());
    }

    @Operation(summary = "Частичное обновление (deep merge). Неверный JSON / validation → 400")
    @PutMapping(value = "/config", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GameConfig> putConfig(@RequestBody JsonNode body,
                                                @AuthenticationPrincipal AdminPrincipal principal) {
        if (body == null || body.isNull() || !body.isObject()) {
            throw GameException.configValidationFailed("Config body must be a JSON object");
        }
        return ResponseEntity.ok(configService.mergeAndApply(body.toString(), appliedBy(principal)));
    }

    @Operation(summary = "История применений конфига (без полного payload)")
    @GetMapping("/config/history")
    public List<ConfigHistoryItemResponse> history(
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return configService.listHistory(limit);
    }

    private static String appliedBy(AdminPrincipal principal) {
        if (principal != null && principal.username() != null && !principal.username().isBlank()) {
            return principal.username();
        }
        return "admin";
    }
}
