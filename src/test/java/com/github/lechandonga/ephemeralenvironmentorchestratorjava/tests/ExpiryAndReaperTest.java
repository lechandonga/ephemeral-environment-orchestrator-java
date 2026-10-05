package com.github.lechandonga.ephemeralenvironmentorchestratorjava.tests;

import com.github.lechandonga.ephemeralenvironmentorchestratorjava.error.ErrorCode;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.model.EphemeralEnvironment;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.model.EnvState;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.service.EnvProvisioner;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.service.EnvironmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.github.lechandonga.ephemeralenvironmentorchestratorjava.tests.ServiceTestSupport.TTL;
import static com.github.lechandonga.ephemeralenvironmentorchestratorjava.tests.ServiceTestSupport.assertErrorCode;
import static com.github.lechandonga.ephemeralenvironmentorchestratorjava.tests.ServiceTestSupport.newService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务三：到期续租规则与自动回收。
 */
@DisplayName("业务三：到期回收策略")
class ExpiryAndReaperTest {

    private MutableClock clock;
    private EnvironmentService service;

    private final EnvProvisioner instantProvisioner = new EnvProvisioner() {
        @Override
        public void provision(EphemeralEnvironment env) {
        }

        @Override
        public void teardown(EphemeralEnvironment env) {
        }
    };

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        service = newService(instantProvisioner, clock, 3);
    }

    @Test
    @DisplayName("用例3.1 到期前续租成功，到期时间被延长")
    void renewBeforeExpiry() {
        EphemeralEnvironment env = service.apply("team-a", "env-1", TTL).environment();

        clock.advance(Duration.ofMinutes(20));
        EphemeralEnvironment renewed =
                service.renew("team-a", "env-1", Duration.ofMinutes(10));

        assertEquals(clock.now().plus(Duration.ofMinutes(20)), renewed.getExpiresAt(),
                "原到期时刻 30 分钟，已过 20 分钟，再续 10 分钟 => 距今 20 分钟");
    }

    @Test
    @DisplayName("用例3.2 到期后不能续租，必须给出已过期原因")
    void renewAfterExpiryRejected() {
        service.apply("team-a", "env-1", TTL);

        clock.advance(Duration.ofMinutes(31));

        assertErrorCode(ErrorCode.ALREADY_EXPIRED,
                () -> service.renew("team-a", "env-1", Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("用例3.3 恰好在到期时刻续租也被拒绝（到期前才允许）")
    void renewAtExactExpiryRejected() {
        service.apply("team-a", "env-1", TTL);

        clock.advance(Duration.ofMinutes(30));

        assertErrorCode(ErrorCode.ALREADY_EXPIRED,
                () -> service.renew("team-a", "env-1", Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("用例3.4 到期后自动回收：状态变已回收、配额释放、查询拿不到")
    void autoReapAfterExpiry() {
        service.apply("team-a", "env-1", TTL);
        assertEquals(1, service.getActiveCount("team-a"));

        clock.advance(Duration.ofMinutes(31));
        int reaped = service.reapExpired();

        assertEquals(1, reaped, "本轮应自动回收 1 个环境");
        assertEquals(0, service.getActiveCount("team-a"), "回收后配额必须释放");
        assertErrorCode(ErrorCode.ENV_RECLAIMED,
                () -> service.getAvailable("team-a", "env-1"));
    }

    @Test
    @DisplayName("用例3.5 未到期的环境不会被自动回收")
    void notExpiredNotReaped() {
        service.apply("team-a", "env-1", TTL);

        clock.advance(Duration.ofMinutes(5));
        int reaped = service.reapExpired();

        assertEquals(0, reaped);
        assertEquals(EnvState.READY,
                service.getAvailable("team-a", "env-1").getState());
    }

    @Test
    @DisplayName("用例3.6 回收开始后，续租与可用查询立即被拒绝；回收结束后配额释放")
    void releasingBlocksRenewAndQuery() throws Exception {
        BlockingProvisioner blocking = new BlockingProvisioner();
        blocking.setBlockTeardown(true);
        EnvironmentService blockingService = newService(blocking, clock, 3);
        blockingService.apply("team-a", "env-1", TTL);
        clock.advance(Duration.ofMinutes(31));

        Thread reaper = new Thread(blockingService::reapExpired, "reaper-thread");
        reaper.start();
        blocking.awaitTeardownEntered();

        // teardown 还没结束（RELEASING 窗口）：新到的续租与查询都拿不到可用环境
        assertErrorCode(ErrorCode.ENV_RELEASING,
                () -> blockingService.renew("team-a", "env-1", Duration.ofMinutes(5)));
        assertErrorCode(ErrorCode.ENV_RELEASING,
                () -> blockingService.getAvailable("team-a", "env-1"));
        assertEquals(1, blockingService.getActiveCount("team-a"),
                "回收完成前配额仍占用");

        blocking.allowTeardownFinish();
        reaper.join();

        assertEquals(0, blockingService.getActiveCount("team-a"), "回收完成后配额释放");
        assertErrorCode(ErrorCode.ENV_RECLAIMED,
                () -> blockingService.getAvailable("team-a", "env-1"));
    }

    @Test
    @DisplayName("用例3.7 多个环境同时到期，一轮全部回收")
    void reapMultipleAtOnce() {
        service.apply("team-a", "env-1", TTL);
        service.apply("team-a", "env-2", TTL);
        service.apply("team-b", "env-9", TTL);

        clock.advance(Duration.ofMinutes(31));

        assertEquals(3, service.reapExpired());
        assertEquals(0, service.getActiveCount("team-a"));
        assertEquals(0, service.getActiveCount("team-b"));
    }

    @Test
    @DisplayName("用例3.8 续租成功后环境不会被提前回收")
    void renewedEnvNotReaped() {
        service.apply("team-a", "env-1", TTL);
        clock.advance(Duration.ofMinutes(25));
        service.renew("team-a", "env-1", Duration.ofMinutes(30));

        clock.advance(Duration.ofMinutes(10));
        assertEquals(0, service.reapExpired(),
                "原 TTL 30 分钟时已过 35 分钟，但已续租，不应被回收");

        clock.advance(Duration.ofMinutes(21));
        assertTrue(service.reapExpired() >= 1, "续租后新的到期点过后应被回收");
    }
}
