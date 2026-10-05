package com.github.lechandonga.ephemeralenvironmentorchestratorjava.tests;

import com.github.lechandonga.ephemeralenvironmentorchestratorjava.model.EphemeralEnvironment;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.service.EnvProvisioner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 可在 provision 中途阻塞的模拟供给器，用于稳定复现"申请中"并发竞争窗口。
 */
public class BlockingProvisioner implements EnvProvisioner {

    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch teardownEntered = new CountDownLatch(1);
    private final CountDownLatch teardownRelease = new CountDownLatch(1);

    private volatile EphemeralEnvironment provisioning;
    private volatile boolean blockTeardown;

    public void setBlockTeardown(boolean blockTeardown) {
        this.blockTeardown = blockTeardown;
    }

    @Override
    public void provision(EphemeralEnvironment env) {
        provisioning = env;
        entered.countDown();
        try {
            release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void teardown(EphemeralEnvironment env) {
        if (blockTeardown) {
            teardownEntered.countDown();
            try {
                teardownRelease.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    public void awaitTeardownEntered() throws InterruptedException {
        if (!teardownEntered.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("teardown 未在预期时间内开始");
        }
    }

    public void allowTeardownFinish() {
        teardownRelease.countDown();
    }

    /** 等待首个申请者进入 provision。 */
    public void awaitProvisionEntered() throws InterruptedException {
        if (!entered.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("provision 未在预期时间内开始");
        }
    }

    /** 放行首个申请者，让其完成 provision。 */
    public void allowProvisionFinish() {
        release.countDown();
    }

    public EphemeralEnvironment getProvisioning() {
        return provisioning;
    }
}
