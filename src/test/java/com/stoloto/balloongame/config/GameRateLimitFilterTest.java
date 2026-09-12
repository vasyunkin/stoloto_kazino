package com.stoloto.balloongame.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S15 / TD-04 — expired rate-limit keys are evicted when the map grows.
 */
class GameRateLimitFilterTest {

    private GameRateLimitFilter filter;
    private RateLimitProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setCapacity(40);
        properties.setWindowSeconds(10);
        filter = new GameRateLimitFilter(properties);
    }

    @Test
    void allow_tracksKeys() {
        assertThat(filter.allow("p1")).isTrue();
        assertThat(filter.activeKeyCount()).isEqualTo(1);
    }

    @Test
    void maybeEvictExpired_removesStaleWindows_whenAboveThreshold() {
        long now = System.currentTimeMillis();
        long windowMs = properties.getWindowSeconds() * 1000L;
        long staleStart = now - windowMs - 1;

        for (int i = 0; i < GameRateLimitFilter.EVICT_SIZE_THRESHOLD; i++) {
            filter.putWindowForTest("stale-" + i, staleStart, 1);
        }
        assertThat(filter.activeKeyCount()).isEqualTo(GameRateLimitFilter.EVICT_SIZE_THRESHOLD);

        filter.maybeEvictExpired(now, windowMs);

        assertThat(filter.activeKeyCount()).isZero();
    }

    @Test
    void maybeEvictExpired_skipsWhenBelowThreshold() {
        long now = System.currentTimeMillis();
        long windowMs = properties.getWindowSeconds() * 1000L;
        filter.putWindowForTest("only", now - windowMs - 1, 1);

        filter.maybeEvictExpired(now, windowMs);

        assertThat(filter.activeKeyCount()).isEqualTo(1);
    }
}
