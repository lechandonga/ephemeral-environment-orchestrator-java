package com.github.lechandonga.ephemeralenvironmentorchestratorjava.service;

import com.github.lechandonga.ephemeralenvironmentorchestratorjava.error.BusinessException;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.error.ErrorCode;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.model.EphemeralEnvironment;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.model.EnvState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 临时环境生命周期核心服务（纯内存实现，本地即可运行）。
 *
 * 并发模型：
 * - 全局锁保护存储与配额计数一致性（原子"查重 + 占配额 + 建记录"）；
 * - 每个 (team, envName) 另有键锁，在整个 provision 期间被首个申请者持有，
 *   让并发重复申请以 ALREADY_REQUESTING 明确失败，而不是静默返回半成品；
 * - 回收一旦把状态置为 RELEASING，后续续租/可用查询立即被拒绝。
 */
@Service
public class EnvironmentService {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentService.class);

    private final EnvProvisioner provisioner;
    private final Clock clock;

    private final int teamQuota;
    private final Duration defaultTtl;
    private final Duration maxTtl;

    private final Map<String, EphemeralEnvironment> envs = new ConcurrentHashMap<>();

    /** 团队占用配额计数：REQUESTING/READY/RELEASING 计入，RECLAIMED 后释放。 */
    private final Map<String, AtomicInteger> activeCountByTeam = new ConcurrentHashMap<>();

    private final Map<String, Object> keyLocks = new ConcurrentHashMap<>();

    private final Object globalLock = new Object();

    public EnvironmentService(
            EnvProvisioner provisioner,
            Clock clock,
            @Value("${orchestrator.quota.per-team:3}") int teamQuota,
            @Value("${orchestrator.ttl.default:PT30M}") Duration defaultTtl,
            @Value("${orchestrator.ttl.max:PT2H}") Duration maxTtl) {
        this.provisioner = provisioner;
        this.clock = clock;
        this.teamQuota = teamQuota;
        this.defaultTtl = defaultTtl;
        this.maxTtl = maxTtl;
    }

    private static String key(String team, String envName) {
        return team + "/" + envName;
    }

    private Object lockFor(String key) {
        return keyLocks.computeIfAbsent(key, k -> new Object());
    }

    public int getTeamQuota() {
        return teamQuota;
    }

    public int getActiveCount(String team) {
        AtomicInteger counter = activeCountByTeam.get(team);
        return counter == null ? 0 : counter.get();
    }

    /**
     * 申请环境（幂等）。ttl 可空，为空时使用默认 TTL。
     */
    public ApplyResult apply(String team, String envName, Duration ttl) {
        validate(team, envName);
        if (ttl != null && (ttl.isNegative() || ttl.isZero() || ttl.compareTo(maxTtl) > 0)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "ttl 必须在 (0, " + maxTtl + "] 之间");
        }
        Duration requestedTtl = ttl != null ? ttl : defaultTtl;
        String k = key(team, envName);

        EphemeralEnvironment existing = envs.get(k);
        if (existing != null) {
            return handleExisting(existing);
        }

        synchronized (globalLock) {
            existing = envs.get(k);
            if (existing != null) {
                return handleExisting(existing);
            }
            AtomicInteger counter =
                    activeCountByTeam.computeIfAbsent(team, t -> new AtomicInteger());
            if (counter.get() >= teamQuota) {
                log.warn("业务[申请-配额] 团队 {} 已占用 {} 个环境，达到配额上限 {}",
                        team, counter.get(), teamQuota);
                throw new BusinessException(ErrorCode.QUOTA_EXCEEDED,
                        "团队 " + team + " 同时占用环境数已达上限 " + teamQuota);
            }
            existing = new EphemeralEnvironment(
                    UUID.randomUUID().toString(), team, envName, clock.now());
            envs.put(k, existing);
            counter.incrementAndGet();
            log.info("业务[申请] 团队 {} 申请环境 {}，占用配额 {}/{}",
                    team, envName, counter.get(), teamQuota);
        }

        // 键锁持有到 provision 结束，挡住并发重复申请。
        synchronized (lockFor(k)) {
            EphemeralEnvironment current = envs.get(k);
            if (current == null || current != existing) {
                return current != null ? handleExisting(current)
                        : new ApplyResult(existing, false);
            }
            try {
                provisioner.provision(existing);
            } catch (RuntimeException e) {
                rollbackFailedProvision(k, existing);
                throw e;
            }
            transition(existing, EnvState.READY);
            existing.setExpiresAt(clock.now().plus(requestedTtl));
            log.info("业务[申请-就绪] 环境 {} (id={}) 就绪，到期时间 {}",
                    envName, existing.getId(), existing.getExpiresAt());
            return new ApplyResult(existing, false);
        }
    }

    private ApplyResult handleExisting(EphemeralEnvironment env) {
        return switch (env.getState()) {
            case READY -> {
                log.info("业务[申请-幂等] 命中已就绪环境 {} (id={})，直接返回",
                        env.getEnvName(), env.getId());
                yield new ApplyResult(env, true);
            }
            case REQUESTING -> throw new BusinessException(ErrorCode.ALREADY_REQUESTING,
                    "环境 " + env.getEnvName() + " 已有申请在处理中，请稍后查询");
            case RELEASING -> throw new BusinessException(ErrorCode.ENV_RELEASING,
                    "环境 " + env.getEnvName() + " 正在回收中，不可申请");
            case RECLAIMED -> throw new BusinessException(ErrorCode.ENV_RECLAIMED,
                    "环境 " + env.getEnvName() + " 已回收，请使用新的环境名重新申请");
        };
    }

    private void rollbackFailedProvision(String k, EphemeralEnvironment env) {
        synchronized (globalLock) {
            if (envs.get(k) == env) {
                envs.remove(k);
                AtomicInteger counter = activeCountByTeam.get(env.getTeam());
                if (counter != null) {
                    counter.decrementAndGet();
                }
            }
        }
        log.warn("业务[申请-失败回滚] 环境 {} provision 失败，已释放占用的配额",
                env.getEnvName());
    }

    /** 续租：仅归属团队、环境 READY 且未到期时允许。 */
    public EphemeralEnvironment renew(String team, String envName, Duration extendBy) {
        validate(team, envName);
        if (extendBy == null || extendBy.isNegative() || extendBy.isZero()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "续租时长必须为正数");
        }
        if (extendBy.compareTo(maxTtl) > 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "单次续租时长不能超过 " + maxTtl);
        }
        EphemeralEnvironment env = requireExisting(team, envName);
        checkOwnership(env, team);

        synchronized (lockFor(key(team, envName))) {
            checkOwnership(env, team);
            if (env.getState() == EnvState.RELEASING) {
                throw new BusinessException(ErrorCode.ENV_RELEASING,
                        "环境 " + envName + " 已开始回收，续租被拒绝");
            }
            if (env.getState() == EnvState.RECLAIMED) {
                throw new BusinessException(ErrorCode.ENV_RECLAIMED,
                        "环境 " + envName + " 已回收，无法续租");
            }
            if (env.getState() == EnvState.REQUESTING) {
                throw new BusinessException(ErrorCode.NOT_READY,
                        "环境 " + envName + " 尚在申请中，就绪后才能续租");
            }
            Instant now = clock.now();
            if (!now.isBefore(env.getExpiresAt())) {
                log.warn("业务[续租-过期] 环境 {} 已于 {} 到期，当前 {}，拒绝续租",
                        envName, env.getExpiresAt(), now);
                throw new BusinessException(ErrorCode.ALREADY_EXPIRED,
                        "环境 " + envName + " 已过期，不能续租，请重新申请");
            }
            Instant newExpiry = env.getExpiresAt().plus(extendBy);
            env.setExpiresAt(newExpiry);
            log.info("业务[续租] 团队 {} 续租环境 {}，新到期时间 {}",
                    team, envName, newExpiry);
            return env;
        }
    }

    /** 主动释放：READY/REQUESTING 触发回收；RELEASING/RECLAIMED 幂等返回。 */
    public EphemeralEnvironment release(String team, String envName) {
        validate(team, envName);
        EphemeralEnvironment env = requireExisting(team, envName);
        checkOwnership(env, team);

        synchronized (lockFor(key(team, envName))) {
            checkOwnership(env, team);
            if (env.getState() == EnvState.RECLAIMED
                    || env.getState() == EnvState.RELEASING) {
                log.info("业务[释放-幂等] 环境 {} 当前状态 {}，无需重复释放",
                        envName, env.getState());
                return env;
            }
            startReclaim(env, "团队主动释放");
            finishReclaim(team, envName);
            return env;
        }
    }

    /** 查询可用环境：RELEASING/RECLAIMED 一律拿不到，以明确错误码拒绝。 */
    public EphemeralEnvironment getAvailable(String team, String envName) {
        validate(team, envName);
        EphemeralEnvironment env = requireExisting(team, envName);
        checkOwnership(env, team);
        if (env.getState() == EnvState.RELEASING) {
            throw new BusinessException(ErrorCode.ENV_RELEASING,
                    "环境 " + envName + " 正在回收中，不再可用");
        }
        if (env.getState() == EnvState.RECLAIMED) {
            throw new BusinessException(ErrorCode.ENV_RECLAIMED,
                    "环境 " + envName + " 已回收，不再可用");
        }
        return env;
    }

    /**
     * 到期自动回收：扫描所有 READY 且已到期的环境。返回本次回收数量。
     */
    public int reapExpired() {
        Instant now = clock.now();
        List<EphemeralEnvironment> candidates = new ArrayList<>(envs.values());
        int reaped = 0;
        for (EphemeralEnvironment env : candidates) {
            if (env.getState() != EnvState.READY
                    || env.getExpiresAt() == null
                    || now.isBefore(env.getExpiresAt())) {
                continue;
            }
            String k = key(env.getTeam(), env.getEnvName());
            synchronized (lockFor(k)) {
                if (env.getState() == EnvState.READY
                        && env.getExpiresAt() != null
                        && !clock.now().isBefore(env.getExpiresAt())) {
                    log.info("业务[到期回收] 环境 {} 已于 {} 到期，自动回收",
                            env.getEnvName(), env.getExpiresAt());
                    startReclaim(env, "到期自动回收");
                    finishReclaim(env.getTeam(), env.getEnvName());
                    reaped++;
                }
            }
        }
        return reaped;
    }

    /** 进入回收：合法状态 -> RELEASING。一旦置位，续租/可用查询立即被拒。 */
    private void startReclaim(EphemeralEnvironment env, String reason) {
        transition(env, EnvState.RELEASING);
        log.info("业务[回收中] 环境 {} (id={}) 进入回收中，原因：{}",
                env.getEnvName(), env.getId(), reason);
        provisioner.teardown(env);
    }

    /** 完成回收：RELEASING -> RECLAIMED，并释放一个团队配额。 */
    private void finishReclaim(String team, String envName) {
        EphemeralEnvironment env = envs.get(key(team, envName));
        synchronized (globalLock) {
            if (env != null) {
                transition(env, EnvState.RECLAIMED);
                AtomicInteger counter = activeCountByTeam.get(team);
                if (counter != null) {
                    counter.decrementAndGet();
                }
                log.info("业务[已回收] 环境 {} 回收完成，团队 {} 释放配额（当前 {}/{}）",
                        envName, team,
                        counter == null ? 0 : counter.get(), teamQuota);
            }
        }
    }

    private void transition(EphemeralEnvironment env, EnvState target) {
        EnvState from = env.getState();
        if (!from.canTransitionTo(target)) {
            log.error("业务[非法流转] 环境 {} 试图从 {} 流转到 {}，被状态机拒绝",
                    env.getEnvName(), from, target);
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "非法状态流转: " + from + " -> " + target);
        }
        env.setState(target);
    }

    private EphemeralEnvironment requireExisting(String team, String envName) {
        EphemeralEnvironment env = envs.get(key(team, envName));
        if (env == null) {
            throw new BusinessException(ErrorCode.ENV_NOT_FOUND,
                    "团队 " + team + " 不存在环境 " + envName);
        }
        return env;
    }

    private void checkOwnership(EphemeralEnvironment env, String team) {
        if (!env.getTeam().equals(team)) {
            log.warn("业务[归属校验] 团队 {} 试图操作属于 {} 的环境 {}，拒绝",
                    team, env.getTeam(), env.getEnvName());
            throw new BusinessException(ErrorCode.TEAM_MISMATCH,
                    "环境 " + env.getEnvName() + " 属于团队 " + env.getTeam()
                            + "，团队 " + team + " 无权操作");
        }
    }

    private static void validate(String team, String envName) {
        if (team == null || team.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "team 不能为空");
        }
        if (envName == null || envName.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "envName 不能为空");
        }
    }
}
