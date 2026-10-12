package com.stoloto.balloongame.service;

import com.stoloto.balloongame.api.exception.GameException;
import com.stoloto.balloongame.config.PlayerPrincipal;
import com.stoloto.balloongame.domain.repository.PlayerAccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Logged-in players are the JWT subject. A mismatched {@code X-Player-Id} is rejected.
 * Guests (no bearer token) keep using the header or path id.
 */
@Component
public class PlayerIdentity {

    private final PlayerAccountRepository accountRepository;

    public PlayerIdentity(PlayerAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public String resolve(String claimedId) {
        PlayerPrincipal account = currentAccount();
        if (account != null) {
            if (claimedId != null && !claimedId.isBlank() && !account.playerId().equals(claimedId.trim())) {
                throw GameException.forbidden();
            }
            return account.playerId();
        }
        if (claimedId == null || claimedId.isBlank()) {
            throw GameException.missingPlayerHeader();
        }
        String id = claimedId.trim();
        if (accountRepository.existsByPlayer_ExternalId(id)) {
            throw GameException.authUnauthorized();
        }
        return id;
    }

    public String requireAccount() {
        PlayerPrincipal account = currentAccount();
        if (account == null) {
            throw GameException.authUnauthorized();
        }
        return account.playerId();
    }

    private static PlayerPrincipal currentAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof PlayerPrincipal player ? player : null;
    }
}
