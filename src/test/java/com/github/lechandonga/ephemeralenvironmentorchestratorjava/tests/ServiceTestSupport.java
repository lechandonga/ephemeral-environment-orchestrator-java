package com.github.lechandonga.ephemeralenvironmentorchestratorjava.tests;

import com.github.lechandonga.ephemeralenvironmentorchestratorjava.error.BusinessException;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.error.ErrorCode;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.service.EnvProvisioner;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.service.EnvironmentService;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 测试公共构造与断言工具。 */
public final class ServiceTestSupport {

    public static final Duration TTL = Duration.ofMinutes(30);

    private ServiceTestSupport() {
    }

    public static EnvironmentService newService(
            EnvProvisioner provisioner, MutableClock clock, int quota) {
        return new EnvironmentService(
                provisioner, clock, quota, TTL, Duration.ofHours(2));
    }

    public static void assertErrorCode(
            ErrorCode expected, Class<? extends Throwable> ignore, RunnableThrowing runnable) {
        try {
            runnable.run();
        } catch (BusinessException e) {
            assertEquals(expected, e.getErrorCode(),
                    "期望错误码 " + expected + "，实际 " + e.getErrorCode());
            return;
        }
        throw new AssertionError("期望抛出错误码 " + expected + "，但没有抛出异常");
    }

    public static void assertErrorCode(ErrorCode expected, RunnableThrowing runnable) {
        assertErrorCode(expected, Throwable.class, runnable);
    }

    @FunctionalInterface
    public interface RunnableThrowing {
        void run();
    }
}
