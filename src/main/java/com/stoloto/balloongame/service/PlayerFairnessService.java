package com.stoloto.balloongame.service;

import com.stoloto.balloongame.config.GameConfig;
import com.stoloto.balloongame.domain.entity.Player;
import com.stoloto.balloongame.provablyfair.ProvablyFairService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Per-player seed chain. The active server seed is created before the bet that consumes it
 * (player create, or the rotation at the end of the previous round) and is never chosen
 * inside the start request after the client seed is known, except for rows migrated
 * without a seed — those are armed once on first start.
 */
@Service
@RequiredArgsConstructor
public class PlayerFairnessService {

    private final ProvablyFairService provablyFairService;
    private final ConfigService configService;

    /** Generate a seed when the player has none. Does not rotate an existing commitment. */
    public void arm(Player player) {
        if (player.getPfServerSeed() != null && !player.getPfServerSeed().isBlank()) {
            return;
        }
        rotate(player);
    }

    /** Replace the unused seed. Call after the current seed has been copied onto a round. */
    public void rotate(Player player) {
        GameConfig.ProvablyFairConfig pf = configService.getSnapshot().getProvablyFair();
        String hex = provablyFairService.newServerSeedHex(pf);
        player.setPfServerSeed(hex);
        player.setPfServerSeedHash(provablyFairService.sha256Utf8(hex));
    }
}
