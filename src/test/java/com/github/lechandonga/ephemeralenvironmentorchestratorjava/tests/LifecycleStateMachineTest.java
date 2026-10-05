package com.github.lechandonga.ephemeralenvironmentorchestratorjava.tests;

import com.github.lechandonga.ephemeralenvironmentorchestratorjava.error.ErrorCode;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.model.EphemeralEnvironment;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.model.EnvState;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.service.ApplyResult;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务一：生命周期流转 + 幂等申请。
 */
@DisplayName("业务一：环境生命周期与状态机")
class LifecycleStateMachineTest {

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
    @DisplayName("用例1.1 申请后环境为就绪态，且记录到期时间")
    void applyCreatesReadyEnvironment() {
        ApplyResult result = service.apply("team-a", "env-1", TTL);

        assertFalse(result.idempotentHit(), "首次申请不是幂等命中");
        assertEquals(EnvState.READY, result.environment().getState());
        assertEquals(clock.now().plus(TTL), result.environment().getExpiresAt());
    }

    @Test
    @DisplayName("用例1.2 同团队同名环境重复申请：幂等返回同一实例，不重复创建")
    void duplicateApplyIsIdempotent() {
        ApplyResult first = service.apply("team-a", "env-1", TTL);
        ApplyResult second = service.apply("team-a", "env-1", TTL);

        assertTrue(second.idempotentHit(), "第二次申请必须标记为幂等命中");
        assertEquals(first.environment().getId(), second.environment().getId());
        assertEquals(1, service.getActiveCount("team-a"), "幂等申请不能重复占用配额");
    }

    @Test
    @DisplayName("用例1.3 释放后状态变为已回收，再次申请同名环境被拒绝")
    void releaseMovesToReclaimedAndBlocksReapply() {
        ApplyResult result = service.apply("team-a", "env-1", TTL);

        EphemeralEnvironment released = service.release("team-a", "env-1");
        assertEquals(EnvState.RECLAIMED, released.getState());

        assertErrorCode(ErrorCode.ENV_RECLAIMED,
                () -> service.apply("team-a", "env-1", TTL));
        assertErrorCode(ErrorCode.ENV_RECLAIMED,
                () -> service.getAvailable("team-a", "env-1"));
        assertNotEquals(EnvState.READY, result.environment().getState(),
                "已回收状态不能被复活成就绪态");
    }

    @Test
    @DisplayName("用例1.4 重复释放是幂等的，不会报错也不影响配额")
    void duplicateReleaseIsIdempotent() {
        service.apply("team-a", "env-1", TTL);

        service.release("team-a", "env-1");
        EphemeralEnvironment again = service.release("team-a", "env-1");

        assertEquals(EnvState.RECLAIMED, again.getState());
        assertEquals(0, service.getActiveCount("team-a"));
    }

    @Test
    @DisplayName("用例1.5 已回收环境的续租被拒绝（终态不可流转）")
    void renewReclaimedRejected() {
        service.apply("team-a", "env-1", TTL);
        service.release("team-a", "env-1");

        assertErrorCode(ErrorCode.ENV_RECLAIMED,
                () -> service.renew("team-a", "env-1", Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("用例1.6 状态机直接拒绝非法方向流转（通过已回收->就绪的对外操作体现）")
    void illegalTransitionDirectionRejected() {
        service.apply("team-a", "env-1", TTL);
        service.release("team-a", "env-1");

        // 已回收后，任何想拿到"可用环境"或续命的操作都必须带着可区分原因失败，
        // 而不是把状态偷偷改回 READY。
        assertErrorCode(ErrorCode.ENV_RECLAIMED,
                () -> service.renew("team-a", "env-1", Duration.ofMinutes(1)));
        assertErrorCode(ErrorCode.ENV_RECLAIMED,
                () -> service.getAvailable("team-a", "env-1"));
    }
}
