package com.github.lechandonga.ephemeralenvironmentorchestratorjava.service;

import com.github.lechandonga.ephemeralenvironmentorchestratorjava.model.EphemeralEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 本地模拟的环境供给器：仅睡眠一小段时间并打印日志，不访问任何真实资源。
 * provision 耗时可配置，供并发测试制造“申请中”的竞争窗口。
 */
@Component
public class FakeEnvProvisioner implements EnvProvisioner {

    private static final Logger log = LoggerFactory.getLogger(FakeEnvProvisioner.class);

    private final Duration provisionDuration;

    public FakeEnvProvisioner(
            @Value("${orchestrator.fake.provision-duration:30ms}") Duration provisionDuration) {
        this.provisionDuration = provisionDuration;
    }

    @Override
    public void provision(EphemeralEnvironment env) {
        log.info("[fake-provisioner] 正在为团队 {} 拉起模拟环境 {} (id={})",
                env.getTeam(), env.getEnvName(), env.getId());
        try {
            Thread.sleep(provisionDuration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("provision interrupted", e);
        }
        log.info("[fake-provisioner] 模拟环境 {} (id={}) 已就绪", env.getEnvName(), env.getId());
    }

    @Override
    public void teardown(EphemeralEnvironment env) {
        log.info("[fake-provisioner] 正在回收模拟环境 {} (id={})", env.getEnvName(), env.getId());
        try {
            Thread.sleep(Math.max(1, provisionDuration.toMillis() / 3));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("teardown interrupted", e);
        }
        log.info("[fake-provisioner] 模拟环境 {} (id={}) 已销毁", env.getEnvName(), env.getId());
    }
}
