package com.stoloto.balloongame.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Closes every FLYING round on the server clock, whether or not a client is subscribed.
 */
@Component
@ConditionalOnProperty(name = "app.round-settler.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class FlyingRoundSettler {

    private final RoundRecoveryService recoveryService;

    @Scheduled(fixedRateString = "${app.ws.tick-interval-ms:200}")
    public void settle() {
        recoveryService.settleLiveRounds();
    }
}
