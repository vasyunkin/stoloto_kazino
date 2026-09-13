package com.stoloto.balloongame.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /**
     * Origin patterns for browser CORS (Spring {@code allowedOriginPatterns}).
     * Prefer patterns over exact hosts so trycloudflare.com tunnels work.
     */
    private List<String> allowedOriginPatterns = new ArrayList<>(List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "https://*.trycloudflare.com",
            "https://*.cloudflaretunnel.com",
            "https://smart-stock.site",
            "https://www.smart-stock.site"
    ));

    /**
     * Exact origins kept for SockJS whitelist / backward compat.
     * Prefer {@link #allowedOriginPatterns} for MVC CORS.
     */
    private List<String> allowedOrigins = new ArrayList<>(List.of(
            "http://localhost:3000",
            "http://localhost:5173",
            "http://127.0.0.1:3000",
            "http://127.0.0.1:5173",
            "https://smart-stock.site",
            "https://www.smart-stock.site"
    ));
}
