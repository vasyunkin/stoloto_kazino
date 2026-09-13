package com.stoloto.balloongame.service;

import com.stoloto.balloongame.config.AdminAuthProperties;
import com.stoloto.balloongame.config.AdminPrincipal;
import com.stoloto.balloongame.domain.entity.AdminUserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    public enum Failure { EXPIRED, INVALID }

    public sealed interface ParseResult {
        record Ok(AdminPrincipal principal) implements ParseResult {}
        record Err(Failure failure) implements ParseResult {}
    }

    private final SecretKey key;
    private final AdminAuthProperties properties;
    private final Clock clock;

    public JwtService(AdminAuthProperties properties, Clock clock) {
        byte[] bytes = properties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(bytes);
        this.properties = properties;
        this.clock = clock;
    }

    public String createAccessToken(AdminUserEntity user) {
        Instant now = clock.instant();
        Instant exp = now.plus(properties.getAccessTokenTtl());
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("role", "ADMIN")
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public long accessExpiresInSeconds() {
        return properties.getAccessTokenTtl().toSeconds();
    }

    public ParseResult parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            UUID id = UUID.fromString(claims.getSubject());
            String username = claims.get("username", String.class);
            String role = claims.get("role", String.class);
            if (username == null || username.isBlank()) {
                return new ParseResult.Err(Failure.INVALID);
            }
            return new ParseResult.Ok(new AdminPrincipal(id, username, role == null ? "ADMIN" : role));
        } catch (ExpiredJwtException e) {
            return new ParseResult.Err(Failure.EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            return new ParseResult.Err(Failure.INVALID);
        }
    }
}
