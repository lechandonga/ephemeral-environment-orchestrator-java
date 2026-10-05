package com.github.lechandonga.ephemeralenvironmentorchestratorjava.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时扫描到期环境并触发回收。本地调度，无外部依赖。
 */
@Component
public class ExpiredEnvironmentReaper {

    private static final Logger log = LoggerFactory.getLogger(ExpiredEnvironmentReaper.class);

    private final EnvironmentService environmentService;

    public ExpiredEnvironmentReaper(EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    @Scheduled(fixedDelayString = "${orchestrator.reaper-interval-millis:500}")
    public void scan() {
        int reclaimed = environmentService.reclaimExpired();
        if (reclaimed > 0) {
            log.info("到期回收扫描完成，本轮回收环境数={}", reclaimed);
        }
    }
}
