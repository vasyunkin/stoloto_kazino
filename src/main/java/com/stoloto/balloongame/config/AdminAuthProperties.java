package com.stoloto.balloongame.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Auth-only settings under {@code game.admin.*}. Kept off {@link GameConfig}
 * so PUT /config merge cannot leak or overwrite JWT secrets.
 */
@Data
@ConfigurationProperties(prefix = "game.admin")
public class AdminAuthProperties {

    private String jwtSecret = "change-me-jwt-secret-use-long-value-32b";

    private Duration accessTokenTtl = Duration.ofMinutes(20);

    private Duration refreshTokenTtl = Duration.ofDays(14);

    private boolean legacyApiKeyEnabled = true;

    private String bootstrapUsername = "admin";

    private String bootstrapPassword = "admin";
}
