package com.stoloto.balloongame.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cheap in-memory window limiter for start/cashout spam. Not a substitute for game locks (I4).
 * Disabled in the test profile so integration loops are not throttled.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class GameRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !(path.equals("/api/game/start") || path.startsWith("/api/game/cashout/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = rateKey(request);
        if (!allow(key)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    "{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String rateKey(HttpServletRequest request) {
        String player = request.getHeader("X-Player-Id");
        if (player != null && !player.isBlank()) {
            return player;
        }
        return request.getRemoteAddr();
    }

    private boolean allow(String key) {
        long now = System.currentTimeMillis();
        long windowMs = properties.getWindowSeconds() * 1000L;
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startMs >= windowMs) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });
        return window.count.get() <= properties.getCapacity();
    }

    private record Window(long startMs, AtomicInteger count) {}
}
