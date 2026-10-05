package com.github.lechandonga.ephemeralenvironmentorchestratorjava.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 临时环境生命周期状态。
 *
 * 合法流转方向：
 * REQUESTING(申请中) -> READY(就绪)
 * REQUESTING(申请中) -> RELEASING(回收中)   申请中被释放/到期
 * READY(就绪)       -> RELEASING(回收中)
 * RELEASING(回收中) -> RECLAIMED(已回收)
 *
 * RECLAIMED 是终态，任何从 RECLAIMED 出发的流转（例如被改回 READY）都会被拒绝。
 */
public enum EnvState {
    REQUESTING,
    READY,
    RELEASING,
    RECLAIMED;

    private static final Map<EnvState, Set<EnvState>> ALLOWED;

    static {
        ALLOWED = new EnumMap<>(EnvState.class);
        ALLOWED.put(REQUESTING, EnumSet.of(READY, RELEASING));
        ALLOWED.put(READY, EnumSet.of(RELEASING));
        ALLOWED.put(RELEASING, EnumSet.of(RECLAIMED));
        ALLOWED.put(RECLAIMED, EnumSet.noneOf(EnvState.class));
    }

    public boolean canTransitionTo(EnvState target) {
        return ALLOWED.getOrDefault(this, EnumSet.noneOf(EnvState.class)).contains(target);
    }
}
