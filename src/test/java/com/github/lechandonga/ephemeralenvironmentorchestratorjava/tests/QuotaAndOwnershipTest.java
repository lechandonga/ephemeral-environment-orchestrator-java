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

/**
 * 业务二：配额边界 + 跨团队归属校验。
 */
@DisplayName("业务二：配额与归属校验")
class QuotaAndOwnershipTest {

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
        service = newService(instantProvisioner, clock, 2);
    }

    @Test
    @DisplayName("用例2.1 占用数恰好达到配额允许，再申请下一个被明确拒绝")
    void quotaBoundary() {
        service.apply("team-a", "env-1", TTL);
        service.apply("team-a", "env-2", TTL);
        assertEquals(2, service.getActiveCount("team-a"));

        assertErrorCode(ErrorCode.QUOTA_EXCEEDED,
                () -> service.apply("team-a", "env-3", TTL));
        assertEquals(2, service.getActiveCount("team-a"), "超配额申请不能占用名额");
    }

    @Test
    @DisplayName("用例2.2 回收一个环境后配额释放，可以再申请新环境")
    void quotaReleasedAfterReclaim() {
        service.apply("team-a", "env-1", TTL);
        service.apply("team-a", "env-2", TTL);

        service.release("team-a", "env-1");
        assertEquals(1, service.getActiveCount("team-a"));

        service.apply("team-a", "env-3", TTL);
        assertEquals(2, service.getActiveCount("team-a"), "回收后释放的配额必须可复用");
    }

    @Test
    @DisplayName("用例2.3 配额按团队隔离，团队间互不影响")
    void quotaIsPerTeam() {
        service.apply("team-a", "env-1", TTL);
        service.apply("team-a", "env-2", TTL);

        service.apply("team-b", "env-1", TTL);
        assertEquals(1, service.getActiveCount("team-b"));
        assertErrorCode(ErrorCode.QUOTA_EXCEEDED,
                () -> service.apply("team-a", "env-x", TTL));
        assertEquals(1, service.getActiveCount("team-b"), "team-a 满配额不影响 team-b");
    }

    @Test
    @DisplayName("用例2.4 别的团队不能续租/释放/查询不属于自己的环境")
    void crossTeamOperationsRejected() {
        service.apply("team-a", "shared-env", TTL);

        assertErrorCode(ErrorCode.TEAM_MISMATCH,
                () -> service.renew("team-b", "shared-env", Duration.ofMinutes(5)));
        assertErrorCode(ErrorCode.TEAM_MISMATCH,
                () -> service.release("team-b", "shared-env"));
        assertErrorCode(ErrorCode.TEAM_MISMATCH,
                () -> service.getAvailable("team-b", "shared-env"));
        assertEquals(EnvState.READY,
                service.getAvailable("team-a", "shared-env").getState(),
                "越权操作绝不能改变环境状态");
    }

    @Test
    @DisplayName("用例2.5 不同团队可以各自申请同名环境，互不干扰")
    void sameEnvNameForDifferentTeams() {
        var a = service.apply("team-a", "same-name", TTL);
        var b = service.apply("team-b", "same-name", TTL);

        assertEquals(EnvState.READY, a.environment().getState());
        assertEquals(EnvState.READY, b.environment().getState());
        assertEquals(1, service.getActiveCount("team-a"));
        assertEquals(1, service.getActiveCount("team-b"));
    }
}
