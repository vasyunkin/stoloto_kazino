package com.stoloto.balloongame.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Application-level beans.
 *
 * Clock is exposed as a bean so it can be mocked in tests (I3 — single
 * source of truth for elapsed time in CrashMathService / GameClockService).
 */
@Configuration
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
