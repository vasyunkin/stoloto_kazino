package com.stoloto.balloongame.domain.repository;

import com.stoloto.balloongame.domain.entity.PlayerAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlayerAccountRepository extends JpaRepository<PlayerAccountEntity, UUID> {

    Optional<PlayerAccountEntity> findByEmail(String email);

    boolean existsByPlayer_ExternalId(String externalId);
}
