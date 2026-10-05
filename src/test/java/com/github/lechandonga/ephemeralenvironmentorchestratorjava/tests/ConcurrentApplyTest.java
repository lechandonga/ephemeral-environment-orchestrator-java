package com.github.lechandonga.ephemeralenvironmentorchestratorjava.tests;

import com.github.lechandonga.ephemeralenvironmentorchestratorjava.error.BusinessException;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.error.ErrorCode;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.model.EphemeralEnvironment;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.model.EnvState;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.service.ApplyResult;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.service.EnvironmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.lechandonga.ephemeralenvironmentorchestratorjava.tests.ServiceTestSupport.TTL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务四：并发申请抢占安全。
 */
@DisplayName("业务四：并发抢占与配额安全")
class ConcurrentApplyTest {

    @Test
    @DisplayName("用例4.1 同一环境并发申请：恰好一个成功，其余以 ALREADY_REQUESTING 失败")
    void sameEnvConcurrentApplyOnlyOneSucceeds() throws Exception {
        MutableClock clock = new MutableClock();
        BlockingProvisioner provisioner = new BlockingProvisioner();
        EnvironmentService service =
                ServiceTestSupport.newService(provisioner, clock, 5);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger alreadyRequesting = new AtomicInteger();
        Set<String> createdIds = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                start.await();
                try {
                    ApplyResult result = service.apply("team-a", "hot-env", TTL);
                    success.incrementAndGet();
                    createdIds.add(result.environment().getId());
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.ALREADY_REQUESTING) {
                        alreadyRequesting.incrementAndGet();
                    } else {
                        throw e;
                    }
                }
                return null;
            });
        }

        start.countDown();
        provisioner.awaitProvisionEntered();
        // 给竞争线程足够时间撞上"申请中"窗口
        Thread.sleep(200);
        provisioner.allowProvisionFinish();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(1, success.get(), "同一环境只能有一个申请成功");
        assertEquals(threads - 1, alreadyRequesting.get(),
                "其余并发请求必须以明确的 ALREADY_REQUESTING 原因失败");
        assertEquals(1, createdIds.size(), "只能创建一个环境实例");
        assertEquals(1, service.getActiveCount("team-a"),
                "失败的并发申请不能重复占用配额");
        assertEquals(EnvState.READY,
                service.getAvailable("team-a", "hot-env").getState());
    }

    @Test
    @DisplayName("用例4.2 配额边界并发：超出配额的申请全部失败，占用数绝不超过上限")
    void quotaNeverExceededUnderConcurrency() throws Exception {
        MutableClock clock = new MutableClock();
        int quota = 3;
        EnvironmentService service = ServiceTestSupport.newService(
                new NoopProvisioner(), clock, quota);

        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger quotaFailed = new AtomicInteger();
        Set<String> activeEnvs = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            pool.submit(() -> {
                start.await();
                try {
                    ApplyResult r = service.apply("team-a", "env-" + idx, TTL);
                    success.incrementAndGet();
                    activeEnvs.add(r.environment().getId());
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.QUOTA_EXCEEDED) {
                        quotaFailed.incrementAndGet();
                    } else {
                        throw e;
                    }
                }
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(quota, success.get(), "成功创建数恰好等于配额");
        assertEquals(threads - quota, quotaFailed.get(), "其余请求因超配额失败");
        assertEquals(quota, service.getActiveCount("team-a"), "占用计数不能超过配额");
        assertEquals(quota, activeEnvs.size());
    }

    @Test
    @DisplayName("用例4.3 并发申请与到期回收交错：回收后配额释放，失败请求不残留占用")
    void concurrentApplyAndReapKeepQuotaConsistent() throws Exception {
        MutableClock clock = new MutableClock();
        BlockingProvisioner provisioner = new BlockingProvisioner();
        EnvironmentService service =
                ServiceTestSupport.newService(provisioner, clock, 1);

        // 先占满唯一配额
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        AtomicInteger quotaFailed = new AtomicInteger();

        pool.submit(() -> {
            try {
                start.await();
                service.apply("team-a", "only-slot", TTL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        start.countDown();
        provisioner.awaitProvisionEntered();

        // 占满期间的并发申请必须超配额失败
        for (int i = 0; i < 3; i++) {
            pool.submit(() -> {
                try {
                    service.apply("team-a", "other", TTL);
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.QUOTA_EXCEEDED) {
                        quotaFailed.incrementAndGet();
                    }
                }
            });
        }
        Thread.sleep(200);
        provisioner.allowProvisionFinish();

        // 到期回收后，唯一配额应被干净释放，可以再申请
        clock.advance(Duration.ofMinutes(31));
        assertEquals(1, service.reapExpired());
        assertEquals(0, service.getActiveCount("team-a"));

        EphemeralEnvironment second =
                service.apply("team-a", "new-slot", TTL).environment();
        assertEquals(EnvState.READY, second.getState());
        assertEquals(1, service.getActiveCount("team-a"));

        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(3, quotaFailed.get());
    }

    private static class NoopProvisioner
            implements com.github.lechandonga.ephemeralenvironmentorchestratorjava.service.EnvProvisioner {
        @Override
        public void provision(EphemeralEnvironment env) {
        }

        @Override
        public void teardown(EphemeralEnvironment env) {
        }
    }
}
