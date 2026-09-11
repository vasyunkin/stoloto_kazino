package com.stoloto.balloongame.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.stoloto.balloongame.api.exception.GameException;
import com.stoloto.balloongame.config.GameConfig;
import com.stoloto.balloongame.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runtime config. Guarded by {@code X-Admin-Key} (see AdminKeyFilter).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Runtime-конфиг. Заголовок X-Admin-Key. apiKey в JSON не отдаётся и не меняется")
@SecurityRequirement(name = "AdminKey")
public class AdminController {

    private final ConfigService configService;

    @Operation(summary = "Текущий runtime-снимок конфига (без apiKey)")
    @GetMapping("/config")
    public ResponseEntity<GameConfig> getConfig() {
        return ResponseEntity.ok(configService.getSnapshot());
    }

    @Operation(summary = "Частичное обновление (deep merge). Неверный JSON / validation → 400")
    @PutMapping(value = "/config", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GameConfig> putConfig(@RequestBody JsonNode body) {
        if (body == null || body.isNull() || !body.isObject()) {
            throw GameException.configValidationFailed("Config body must be a JSON object");
        }
        return ResponseEntity.ok(configService.mergeAndApply(body.toString(), "admin"));
    }
}
