package com.github.lechandonga.ephemeralenvironmentorchestratorjava.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 到期自动回收调度器。固定间隔扫描，触发 EnvironmentService 的回收流程。
 * 可通过 orchestrator.reaper.enabled=false 关闭（单元测试中通常直接调用 reapExpired）。
 */
@Component
@ConditionalOnProperty(
        name = "orchestrator.reaper.enabled", havingValue = "true", matchIfMissing = true)
public class ExpiryReaper {

    private static final Logger log = LoggerFactory.getLogger(ExpiryReaper.class);

    private final EnvironmentService environmentService;

    public ExpiryReaper(EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    @Scheduled(fixedDelayString = "${orchestrator.reaper.interval-ms:2000}")
    public void scan() {
        int reaped = environmentService.reapExpired();
        if (reaped > 0) {
            log.info("业务[到期回收] 本轮自动回收环境数量：{}", reaped);
        }
    }
}
