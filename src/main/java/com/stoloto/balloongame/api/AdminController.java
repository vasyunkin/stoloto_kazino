package com.stoloto.balloongame.api;

import com.stoloto.balloongame.config.GameConfig;
import com.stoloto.balloongame.service.ConfigService;
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
public class AdminController {

    private final ConfigService configService;

    @GetMapping("/config")
    public ResponseEntity<GameConfig> getConfig() {
        return ResponseEntity.ok(configService.getSnapshot());
    }

    @PutMapping(value = "/config", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GameConfig> putConfig(@RequestBody String body) {
        return ResponseEntity.ok(configService.mergeAndApply(body, "admin"));
    }
}
