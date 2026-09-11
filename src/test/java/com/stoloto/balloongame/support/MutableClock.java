package com.stoloto.balloongame.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test clock: freeze/advance time so crash vs cashout can be driven deterministically (I3).
 */
public final class MutableClock extends Clock {

    private final AtomicReference<Clock> delegate = new AtomicReference<>(Clock.systemUTC());

    public void freeze(Instant instant) {
        delegate.set(Clock.fixed(instant, ZoneOffset.UTC));
    }

    public void useSystemUtc() {
        delegate.set(Clock.systemUTC());
    }

    @Override
    public ZoneId getZone() {
        return delegate.get().getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return delegate.get().withZone(zone);
    }

    @Override
    public Instant instant() {
        return delegate.get().instant();
    }
}
