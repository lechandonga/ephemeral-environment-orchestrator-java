package com.github.lechandonga.ephemeralenvironmentorchestratorjava.tests;

import com.github.lechandonga.ephemeralenvironmentorchestratorjava.model.EnvState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务一补充：状态机允许/禁止方向的穷举校验。
 */
@DisplayName("业务一：状态机合法方向矩阵")
class EnvStateMachineTest {

    @Test
    @DisplayName("用例0.1 只允许 REQUESTING→READY/RELEASING、READY→RELEASING、RELEASING→RECLAIMED")
    void allowedTransitions() {
        assertTrue(EnvState.REQUESTING.canTransitionTo(EnvState.READY));
        assertTrue(EnvState.REQUESTING.canTransitionTo(EnvState.RELEASING));
        assertTrue(EnvState.READY.canTransitionTo(EnvState.RELEASING));
        assertTrue(EnvState.RELEASING.canTransitionTo(EnvState.RECLAIMED));
    }

    @Test
    @DisplayName("用例0.2 已回收是终态，已回收→就绪/任何方向都被拒绝；也不允许跳级")
    void forbiddenTransitions() {
        for (EnvState target : EnvState.values()) {
            assertFalse(EnvState.RECLAIMED.canTransitionTo(target),
                    "RECLAIMED 不能流转到 " + target);
        }
        assertFalse(EnvState.READY.canTransitionTo(EnvState.REQUESTING));
        assertFalse(EnvState.READY.canTransitionTo(EnvState.RECLAIMED), "不能从就绪直接跳到已回收");
        assertFalse(EnvState.REQUESTING.canTransitionTo(EnvState.RECLAIMED), "不能从申请中直接跳到已回收");
        assertFalse(EnvState.RELEASING.canTransitionTo(EnvState.READY), "回收中不能退回就绪");
        assertFalse(EnvState.RELEASING.canTransitionTo(EnvState.REQUESTING));
    }
}
