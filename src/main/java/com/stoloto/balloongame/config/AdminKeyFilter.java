package com.stoloto.balloongame.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Servlet filter: guards all /api/admin/** endpoints with the X-Admin-Key header.
 * Missing or wrong key → 403 (without detail, to avoid leaking key existence).
 *
 * In v0 we keep this simple (no Spring Security) to stay fast for the hackathon.
 * For the final: swap to Spring Security Basic/OAuth2.
 */
@Component
@RequiredArgsConstructor
public class AdminKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Admin-Key";
    private static final String ADMIN_PATH_PREFIX = "/api/admin";

    private final GameConfig gameConfig;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (!path.startsWith(ADMIN_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader(HEADER);
        String expectedKey = gameConfig.getAdmin().getApiKey();

        if (expectedKey == null || !expectedKey.equals(providedKey)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    "{\"code\":\"FORBIDDEN\",\"message\":\"Invalid or missing X-Admin-Key\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }
}
