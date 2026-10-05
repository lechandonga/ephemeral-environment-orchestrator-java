package com.github.lechandonga.ephemeralenvironmentorchestratorjava.service;

import com.github.lechandonga.ephemeralenvironmentorchestratorjava.domain.EphemeralEnvironment;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;
import org.springframework.stereotype.Component;

/**
 * 内存版环境存储与团队配额计数器（本地模拟数据库）。
 *
 * <p>环境终态 RECLAIMED 后会从索引中移除并释放配额；
 * 配额计数通过 {@link ConcurrentHashMap#compute} 原子更新，
 * 保证并发下既不会超配，回收后也不会漏释放。
 */
@Component
public class EnvironmentRepository {

    private final ConcurrentHashMap<String, EphemeralEnvironment> byKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> idToKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> activeCountByTeam = new ConcurrentHashMap<>();

    private static String keyOf(String team, String envKey) {
        return team + "#" + envKey;
    }

    public EphemeralEnvironment find(String team, String envKey) {
        return byKey.get(keyOf(team, envKey));
    }

    public EphemeralEnvironment findById(String id) {
        String key = idToKey.get(id);
        return key == null ? null : byKey.get(key);
    }

    public Collection<EphemeralEnvironment> findAll() {
        return byKey.values();
    }

    /**
     * 原子地“检查配额并占用一个名额后放入新环境”。
     *
     * @return 放入成功返回 true；已达到团队配额上限返回 false，不产生任何占用
     */
    public boolean tryOccupyAndPut(EphemeralEnvironment env, int teamQuota) {
        final boolean[] accepted = {false};
        activeCountByTeam.compute(env.getTeam(), (team, current) -> {
            int count = current == null ? 0 : current.get();
            if (count >= teamQuota) {
                return current;
            }
            accepted[0] = true;
            AtomicInteger next = current == null ? new AtomicInteger(0) : current;
            next.incrementAndGet();
            return next;
        });
        if (!accepted[0]) {
            return false;
        }
        byKey.put(keyOf(env.getTeam(), env.getEnvKey()), env);
        idToKey.put(env.getId(), keyOf(env.getTeam(), env.getEnvKey()));
        return true;
    }

    /**
     * 终态回收：移除索引并释放一个团队配额名额。每个环境只移除一次。
     */
    public void removeAndRelease(EphemeralEnvironment env) {
        String key = keyOf(env.getTeam(), env.getEnvKey());
        boolean removed = byKey.remove(key, env);
        idToKey.remove(env.getId(), key);
        if (removed) {
            activeCountByTeam.compute(env.getTeam(), (team, current) -> {
                if (current != null) {
                    current.decrementAndGet();
                }
                return current;
            });
        }
    }

    public int activeCount(String team) {
        AtomicInteger counter = activeCountByTeam.get(team);
        return counter == null ? 0 : counter.get();
    }

    /** 仅供测试/管理使用：原子调整团队计数。 */
    int updateCount(String team, IntUnaryOperator op) {
        AtomicInteger counter = activeCountByTeam.computeIfAbsent(team, t -> new AtomicInteger(0));
        return counter.updateAndGet(op);
    }
}
