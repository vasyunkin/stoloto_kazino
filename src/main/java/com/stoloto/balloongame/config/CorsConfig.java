package com.stoloto.balloongame.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class CorsConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Patterns (not only exact origins) so Cloudflare quick tunnels work:
        // https://*.trycloudflare.com
        String[] patterns = corsProperties.getAllowedOriginPatterns().toArray(String[]::new);
        registry.addMapping("/api/**")
                .allowedOriginPatterns(patterns)
                .allowedMethods("GET", "POST", "PUT", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Player-Id", "X-Admin-Key")
                .exposedHeaders("Content-Type")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
