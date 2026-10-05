package com.github.lechandonga.ephemeralenvironmentorchestratorjava.service;

import com.github.lechandonga.ephemeralenvironmentorchestratorjava.config.OrchestratorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 本地模拟的底层“基础设施平台”：不连接任何真实集群或云服务，
 * 仅通过短暂延时模拟创建/销毁动作，并返回一个本地伪地址。
 */
@Component
public class MockInfrastructureClient {

    private static final Logger log = LoggerFactory.getLogger(MockInfrastructureClient.class);

    private final OrchestratorProperties properties;

    public MockInfrastructureClient(OrchestratorProperties properties) {
        this.properties = properties;
    }

    public String provision(String team, String envKey) {
        sleep(properties.getProvisionDelayMillis());
        String endpoint = "http://%s-%s.local-mock:8080".formatted(team, envKey);
        log.info("[mock-infra] 已在本地模拟创建环境 team={}, envKey={}, endpoint={}", team, envKey, endpoint);
        return endpoint;
    }

    public void teardown(String endpoint) {
        sleep(Math.max(1, properties.getProvisionDelayMillis() / 3));
        log.info("[mock-infra] 已在本地模拟销毁环境 endpoint={}", endpoint);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("模拟基础设施操作被中断", e);
        }
    }
}
