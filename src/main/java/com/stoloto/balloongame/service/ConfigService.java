package com.stoloto.balloongame.service;

import com.stoloto.balloongame.config.GameConfig;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the current game configuration for <em>new</em> rounds.
 *
 * <p>I8: callers must use {@link #getSnapshot()} — never the live
 * {@code @ConfigurationProperties} bean — and store that copy on the session.
 * {@link #update(GameConfig)} is a stub for S8 admin PUT; not exposed via HTTP in S4.
 */
@Service
public class ConfigService {

    private final AtomicReference<GameConfig> current;

    public ConfigService(GameConfig initialConfig) {
        this.current = new AtomicReference<>(initialConfig);
    }

    /**
     * Deep copy of the current config. Never returns the live mutable bean.
     */
    public GameConfig getSnapshot() {
        return current.get().deepCopy();
    }

    /**
     * Atomic swap used by admin PUT in S8. Not wired to an endpoint in S4.
     */
    public void update(GameConfig newConfig) {
        current.set(newConfig);
    }
}
