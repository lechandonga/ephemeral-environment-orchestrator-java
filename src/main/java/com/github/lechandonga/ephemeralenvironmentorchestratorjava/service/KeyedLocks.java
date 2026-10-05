package com.github.lechandonga.ephemeralenvironmentorchestratorjava.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

/**
 * 按 (team, envKey) 分桶的互斥锁集合。
 *
 * <p>同一个环境的申请/续租/回收串行化，不同环境之间互不阻塞，
 * 是并发申请幂等与状态机合法性的基础。锁本身按需创建并长期复用
 * （环境 key 数量有限，且锁对象极小，避免过期清理带来的竞态）。
 */
@Component
public class KeyedLocks {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ReentrantLock lockFor(String team, String envKey) {
        return locks.computeIfAbsent(team + "#" + envKey, key -> new ReentrantLock());
    }
}
