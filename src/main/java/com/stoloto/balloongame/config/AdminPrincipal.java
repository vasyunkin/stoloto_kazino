package com.stoloto.balloongame.config;

import java.util.UUID;

/**
 * Security principal for JWT (id + username) or legacy X-Admin-Key (id null).
 */
public record AdminPrincipal(UUID id, String username, String role) {
}
