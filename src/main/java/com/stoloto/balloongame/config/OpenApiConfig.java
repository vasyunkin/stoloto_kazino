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
                                ## Как потыкать в Swagger
                                
                                1. Справа сверху **Authorize**.
                                2. **PlayerId** = любая строка, например `demo` (это не логин).
                                3. **AdminKey** = `change-me-in-prod` (дефолт Docker / application.yml). `test-admin-key` работает только в тестах.
                                4. Players → `POST /deposit` на игрока `demo`, amount например `1000`.
                                5. Game → `POST /start` с тем же `playerId: "demo"`. В ответе будет **настоящий** `gameId`.
                                6. Этот UUID копируете в state / cashout / verify. Пример `3fa85f64-5717-...` — заглушка Swagger, такого раунда нет.
                                
                                Пока status=FLYING, crashPoint и serverSeed в ответах нет.
                                """))
                .components(new Components()
                        .addSecuritySchemes("PlayerId", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Player-Id")
                                .description("Любая строка (например demo). Должна совпадать с playerId в POST /start."))
                        .addSecuritySchemes("AdminKey", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Admin-Key")
                                .description("По умолчанию change-me-in-prod. Не test-admin-key — тот только в @ActiveProfiles(test).")));
    }
}
