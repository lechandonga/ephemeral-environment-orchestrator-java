package com.github.lechandonga.ephemeralenvironmentorchestratorjava.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 临时环境的生命周期状态。
 *
 * <pre>
 * REQUESTING --&gt; READY --&gt; RELEASING --&gt; RECLAIMED
 *     |                          ^
 *     +--------------------------+  (申请/就绪后可被释放)
 * </pre>
 *
 * 任何不在 {@link #LEGAL_TRANSITIONS} 中的流转都必须被拒绝，
 * 例如 RECLAIMED -&gt; READY 是明确非法的。
 */
public enum EnvStatus {
    REQUESTING,
    READY,
    RELEASING,
    RECLAIMED;

    private static final Map<EnvStatus, Set<EnvStatus>> LEGAL_TRANSITIONS;

    static {
        Map<EnvStatus, Set<EnvStatus>> transitions = new EnumMap<>(EnvStatus.class);
        transitions.put(REQUESTING, EnumSet.of(READY, RELEASING));
        transitions.put(READY, EnumSet.of(RELEASING));
        transitions.put(RELEASING, EnumSet.of(RECLAIMED));
        transitions.put(RECLAIMED, EnumSet.noneOf(EnvStatus.class));
        LEGAL_TRANSITIONS = transitions;
    }

    /**
     * 判断从当前状态流转到目标状态是否合法。
     */
    public boolean canTransitionTo(EnvStatus target) {
        return LEGAL_TRANSITIONS.getOrDefault(this, EnumSet.noneOf(EnvStatus.class)).contains(target);
    }
}
