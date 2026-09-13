package com.stoloto.balloongame.service;

import com.stoloto.balloongame.api.dto.AdminProfileResponse;
import com.stoloto.balloongame.api.dto.AdminTokenResponse;
import com.stoloto.balloongame.api.exception.GameException;
import com.stoloto.balloongame.config.AdminAuthProperties;
import com.stoloto.balloongame.domain.entity.AdminRefreshTokenEntity;
import com.stoloto.balloongame.domain.entity.AdminUserEntity;
import com.stoloto.balloongame.domain.repository.AdminRefreshTokenRepository;
import com.stoloto.balloongame.domain.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminUserRepository userRepository;
    private final AdminRefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AdminAuthProperties properties;
    private final Clock clock;

    @Transactional
    public AdminTokenResponse login(String username, String password, String userAgent, String ip) {
        AdminUserEntity user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw GameException.authInvalidCredentials();
        }
        if (!user.isEnabled()) {
            throw GameException.authDisabled();
        }
        user.setLastLoginAt(clock.instant());
        return issueTokens(user, userAgent, ip);
    }

    @Transactional
    public AdminTokenResponse refresh(String refreshToken, String userAgent, String ip) {
        AdminRefreshTokenEntity stored = resolveActive(refreshToken);
        AdminUserEntity user = stored.getAdminUser();
        if (!user.isEnabled()) {
            stored.setRevokedAt(clock.instant());
            throw GameException.authDisabled();
        }
        stored.setRevokedAt(clock.instant());
        return issueTokens(user, userAgent, ip);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(sha256Hex(refreshToken)).ifPresent(stored -> {
            if (stored.getRevokedAt() == null) {
                stored.setRevokedAt(clock.instant());
            }
        });
    }

    @Transactional(readOnly = true)
    public AdminProfileResponse me(UUID adminId) {
        AdminUserEntity user = userRepository.findById(adminId)
                .orElseThrow(GameException::authUnauthorized);
        if (!user.isEnabled()) {
            throw GameException.authDisabled();
        }
        return toProfile(user);
    }

    @Transactional
    public void changePassword(UUID adminId, String currentPassword, String newPassword) {
        AdminUserEntity user = userRepository.findById(adminId)
                .orElseThrow(GameException::authUnauthorized);
        if (!user.isEnabled()) {
            throw GameException.authDisabled();
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw GameException.authInvalidCredentials();
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        refreshTokenRepository.revokeAllActiveForUser(user.getId(), clock.instant());
    }

    private AdminTokenResponse issueTokens(AdminUserEntity user, String userAgent, String ip) {
        Instant now = clock.instant();
        String refreshRaw = newRefreshToken();
        AdminRefreshTokenEntity row = new AdminRefreshTokenEntity();
        row.setAdminUser(user);
        row.setTokenHash(sha256Hex(refreshRaw));
        row.setExpiresAt(now.plus(properties.getRefreshTokenTtl()));
        row.setUserAgent(truncate(userAgent, 256));
        row.setIp(truncate(ip, 64));
        refreshTokenRepository.save(row);

        return new AdminTokenResponse(
                jwtService.createAccessToken(user),
                refreshRaw,
                jwtService.accessExpiresInSeconds(),
                toProfile(user)
        );
    }

    private AdminRefreshTokenEntity resolveActive(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw GameException.authUnauthorized();
        }
        AdminRefreshTokenEntity stored = refreshTokenRepository.findByTokenHash(sha256Hex(refreshToken))
                .orElseThrow(GameException::authUnauthorized);
        if (!stored.isActive(clock.instant())) {
            throw GameException.authUnauthorized();
        }
        return stored;
    }

    private static AdminProfileResponse toProfile(AdminUserEntity user) {
        return new AdminProfileResponse(user.getId(), user.getUsername(), user.getDisplayName());
    }

    private static String newRefreshToken() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
