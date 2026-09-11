package com.stoloto.balloongame.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 contract for frontend integration (S9).
 * UI: {@code /swagger-ui.html} · JSON: {@code /v3/api-docs}
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI balloonGameOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Воздушный Шар API")
                        .version("1.0.0")
                        .description("""
                                Crash-игра. Клиент: ставка → poll state каждые 100–250 ms → cashout.
                                Заголовок X-Player-Id обязателен для /api/game/** и должен совпадать с playerId.
                                Пока status=FLYING, crashPoint и serverSeed в ответах нет.
                                Admin: заголовок X-Admin-Key.
                                """))
                .components(new Components()
                        .addSecuritySchemes("AdminKey", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Admin-Key")));
    }
}
