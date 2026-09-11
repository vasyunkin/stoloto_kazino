package com.stoloto.balloongame.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    /** When false (test profile), the filter is a no-op. */
    private boolean enabled = true;

    private int capacity = 40;

    private int windowSeconds = 10;
}
