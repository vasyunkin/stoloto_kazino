package com.stoloto.balloongame.service;

import com.stoloto.balloongame.api.dto.PlayerTokenResponse;
import com.stoloto.balloongame.api.exception.GameException;
import com.stoloto.balloongame.config.AdminAuthProperties;
import com.stoloto.balloongame.domain.entity.Player;
import com.stoloto.balloongame.domain.entity.PlayerAccountEntity;
import com.stoloto.balloongame.domain.entity.PlayerRefreshTokenEntity;
import com.stoloto.balloongame.domain.repository.PlayerAccountRepository;
import com.stoloto.balloongame.domain.repository.PlayerRefreshTokenRepository;
import com.stoloto.balloongame.domain.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlayerAuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PlayerAccountRepository accountRepository;
    private final PlayerRefreshTokenRepository refreshTokenRepository;
    private final PlayerRepository playerRepository;
    private final PlayerService playerService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AdminAuthProperties properties;
    private final Clock clock;

    @Transactional
    public PlayerTokenResponse register(String email, String password) {
        String normalized = normalizeEmail(email);
        if (accountRepository.findByEmail(normalized).isPresent()) {
            throw GameException.emailTaken();
        }
        String playerId = UUID.randomUUID().toString();
        playerService.getWalletOrCreate(playerId);
        Player player = playerRepository.findByExternalId(playerId)
                .orElseThrow(GameException::playerNotFound);
        PlayerAccountEntity account = new PlayerAccountEntity();
        account.setEmail(normalized);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setPlayer(player);
        accountRepository.save(account);
        return issueTokens(account);
    }

    @Transactional
    public PlayerTokenResponse login(String email, String password) {
        PlayerAccountEntity account = accountRepository.findByEmail(normalizeEmail(email)).orElse(null);
        if (account == null || !passwordEncoder.matches(password, account.getPasswordHash())) {
            throw GameException.authInvalidCredentials();
        }
        return issueTokens(account);
    }

    @Transactional
    public PlayerTokenResponse refresh(String refreshToken) {
        PlayerRefreshTokenEntity stored = resolveActive(refreshToken);
        stored.setRevokedAt(clock.instant());
        return issueTokens(stored.getAccount());
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

    private PlayerTokenResponse issueTokens(PlayerAccountEntity account) {
        String refreshRaw = newRefreshToken();
        PlayerRefreshTokenEntity row = new PlayerRefreshTokenEntity();
        row.setAccount(account);
        row.setTokenHash(sha256Hex(refreshRaw));
        row.setExpiresAt(clock.instant().plus(properties.getRefreshTokenTtl()));
        refreshTokenRepository.save(row);
        String playerId = account.getPlayer().getExternalId();
        return new PlayerTokenResponse(
                jwtService.createPlayerAccessToken(playerId),
                refreshRaw,
                jwtService.accessExpiresInSeconds(),
                playerId,
                account.getEmail()
        );
    }

    private PlayerRefreshTokenEntity resolveActive(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw GameException.authUnauthorized();
        }
        PlayerRefreshTokenEntity stored = refreshTokenRepository.findByTokenHash(sha256Hex(refreshToken))
                .orElseThrow(GameException::authUnauthorized);
        if (!stored.isActive(clock.instant())) {
            throw GameException.authUnauthorized();
        }
        return stored;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String newRefreshToken() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
