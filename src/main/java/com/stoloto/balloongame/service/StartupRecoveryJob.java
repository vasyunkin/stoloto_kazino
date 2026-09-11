package com.stoloto.balloongame.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Voids in-flight rounds before the process serves traffic (hackathon restart policy).
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
        log.info("Startup recovery complete: flyingRoundsVoided={}", n);
    }
}
