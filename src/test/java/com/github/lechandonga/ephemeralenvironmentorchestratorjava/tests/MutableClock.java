package com.github.lechandonga.ephemeralenvironmentorchestratorjava.tests;

import com.github.lechandonga.ephemeralenvironmentorchestratorjava.service.Clock;

import java.time.Duration;
import java.time.Instant;

/** 测试用可控时钟：从固定起点开始，可手动前进时间以模拟到期。 */
public class MutableClock implements Clock {

    private volatile Instant now = Instant.parse("2026-10-05T00:00:00Z");

    @Override
    public Instant now() {
        return now;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }
}
