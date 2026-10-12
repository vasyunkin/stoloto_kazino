package com.stoloto.balloongame.config;

import com.stoloto.balloongame.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Profile {@code prod} must not boot with the demo faucet or the checked-in secrets.
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProdSafetyChecker implements ApplicationRunner {

    private final ConfigService configService;
    private final AdminAuthProperties adminAuthProperties;

    @Override
    public void run(ApplicationArguments args) {
        var admin = configService.getSnapshot().getAdmin();
        List<String> problems = new ArrayList<>();
        if (admin.isDemoMode()) {
            problems.add("game.admin.demo-mode must be false");
        }
        if (admin.isAllowDeposit()) {
            problems.add("game.admin.allow-deposit must be false");
        }
        if (adminAuthProperties.isLegacyApiKeyEnabled()) {
            problems.add("game.admin.legacy-api-key-enabled must be false");
        }
        if (adminAuthProperties.getJwtSecret() == null
                || adminAuthProperties.getJwtSecret().startsWith("change-me")) {
            problems.add("ADMIN_JWT_SECRET must be set to a non-default value");
        }
        if ("admin".equals(adminAuthProperties.getBootstrapPassword())) {
            problems.add("ADMIN_BOOTSTRAP_PASSWORD must not be admin");
        }
        if (admin.getApiKey() == null || "change-me-in-prod".equals(admin.getApiKey())) {
            problems.add("GAME_ADMIN_KEY must be set");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start with profile prod: " + String.join("; ", problems));
        }
        log.info("Prod safety checks passed");
    }
}
