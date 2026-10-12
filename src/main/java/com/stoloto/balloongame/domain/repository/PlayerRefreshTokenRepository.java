package com.stoloto.balloongame.domain.repository;

import com.stoloto.balloongame.domain.entity.PlayerRefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlayerRefreshTokenRepository extends JpaRepository<PlayerRefreshTokenEntity, UUID> {

    Optional<PlayerRefreshTokenEntity> findByTokenHash(String tokenHash);
}
