package com.stoloto.balloongame.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stoloto.balloongame.api.exception.ErrorCode;
import com.stoloto.balloongame.api.exception.ErrorResponse;
import com.stoloto.balloongame.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = header.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        JwtService.ParseResult parsed = jwtService.parse(token);
        if (parsed instanceof JwtService.ParseResult.Ok ok) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    ok.principal(),
                    token,
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
            return;
        }
        JwtService.ParseResult.Err err = (JwtService.ParseResult.Err) parsed;
        ErrorCode code = err.failure() == JwtService.Failure.EXPIRED
                ? ErrorCode.AUTH_TOKEN_EXPIRED
                : ErrorCode.AUTH_UNAUTHORIZED;
        writeUnauthorized(response, code, err.failure() == JwtService.Failure.EXPIRED
                ? "Access token expired"
                : "Unauthorized");
    }

    private void writeUnauthorized(HttpServletResponse response, ErrorCode code, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(new ErrorResponse(code, message)));
    }
}
