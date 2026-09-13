package com.stoloto.balloongame.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Dual-mode admin auth (A2): if JWT already authenticated, pass through;
 * otherwise accept legacy {@code X-Admin-Key} when enabled.
 * Does not write 401 itself — Spring Security entry point owns the JSON envelope.
 */
@Component
@RequiredArgsConstructor
public class AdminKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Admin-Key";
    private static final String ADMIN_PATH_PREFIX = "/api/admin";
    private static final String AUTH_PATH_PREFIX = "/api/admin/auth/";

    private final GameConfig gameConfig;
    private final AdminAuthProperties adminAuthProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (!path.startsWith(ADMIN_PATH_PREFIX)) {
            return true;
        }
        return path.startsWith(AUTH_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (adminAuthProperties.isLegacyApiKeyEnabled() && keyMatches(request.getHeader(HEADER))) {
            var principal = new AdminPrincipal(null, "admin", "ADMIN");
            var authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    "legacy-key",
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private boolean keyMatches(String providedKey) {
        String expectedKey = gameConfig.getAdmin().getApiKey();
        if (providedKey == null || expectedKey == null) {
            return false;
        }
        byte[] expected = expectedKey.getBytes(StandardCharsets.UTF_8);
        byte[] provided = providedKey.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }
}
