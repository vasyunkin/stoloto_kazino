package com.stoloto.balloongame.service;

/**
 * Intent for {@link RoundLifecycleService#resolve} — state and cashout share one lock (I4).
 */
public enum ResolveIntent {
    STATE_ONLY,
    CASHOUT
}
