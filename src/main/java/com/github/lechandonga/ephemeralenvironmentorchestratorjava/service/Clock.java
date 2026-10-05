package com.github.lechandonga.ephemeralenvironmentorchestratorjava.service;

import java.time.Instant;

/**
 * 时钟抽象，便于测试中控制时间（模拟到期），生产默认使用系统时间。
 */
public interface Clock {
    Instant now();
}
