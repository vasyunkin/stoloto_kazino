package com.stoloto.balloongame.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Resumes in-flight rounds from the database before the process serves traffic.
 * A round is voided and refunded only when its snapshot cannot be restored.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class StartupRecoveryJob implements ApplicationRunner {

    private final RoundRecoveryService recoveryService;

    @Override
    public void run(ApplicationArguments args) {
        int n = recoveryService.recoverAllFlying();
        log.info("Startup recovery complete: flyingRoundsSeen={}", n);
    }
}
