package com.github.lechandonga.ephemeralenvironmentorchestratorjava.service;

import com.github.lechandonga.ephemeralenvironmentorchestratorjava.config.OrchestratorProperties;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.domain.EnvStatus;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.domain.EphemeralEnvironment;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.domain.ErrorCode;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.error.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 临时环境生命周期核心服务：申请（幂等）、续租、释放、到期回收。
 *
 * <p>并发安全模型：对同一个 (team, envKey) 的所有写操作通过
 * {@link KeyedLocks} 串行化；团队名额占用通过存储层的原子 CAS 完成，
 * 因此“并发申请只成功一个”“配额不重复占用”“回收必释放名额”同时成立。
 */
@Service
public class EnvironmentService {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentService.class);

    private final EnvironmentRepository repository;
    private final KeyedLocks keyedLocks;
    private final MockInfrastructureClient infrastructure;
    private final OrchestratorProperties properties;
    private final Clock clock;

    public EnvironmentService(EnvironmentRepository repository, KeyedLocks keyedLocks,
                              MockInfrastructureClient infrastructure,
                              OrchestratorProperties properties, Clock clock) {
        this.repository = repository;
        this.keyedLocks = keyedLocks;
        this.infrastructure = infrastructure;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 申请环境。同团队同 envKey 重复提交是幂等的：
     * 环境仍可用时直接返回已存在的那一个，不会重复创建；
     * 与另一个正在进行的申请并发时，后到者得到明确的 ENV_PROVISION_CONFLICT。
     */
    public EphemeralEnvironment apply(String team, String envKey, Long ttlSeconds) {
        validateIdentity(team, envKey);
        long ttl = ttlSeconds == null ? properties.getDefaultTtlSeconds() : ttlSeconds;
        if (ttl <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "ttlSeconds 必须为正数");
        }
        ReentrantLock lock = keyedLocks.lockFor(team, envKey);
        lock.lock();
        try {
            EphemeralEnvironment existing = repository.find(team, envKey);
            if (existing != null) {
                return handleExistingOnApply(existing);
            }

            Instant now = Instant.now(clock);
            EphemeralEnvironment env = new EphemeralEnvironment(
                    UUID.randomUUID().toString(), team, envKey, EnvStatus.REQUESTING,
                    now.plusSeconds(ttl), now);

            if (!repository.tryOccupyAndPut(env, properties.getTeamQuota())) {
                throw new BusinessException(ErrorCode.ENV_QUOTA_EXCEEDED,
                        "团队 %s 的环境数量已达配额上限 %d".formatted(team, properties.getTeamQuota()));
            }
            log.info("环境申请已受理 team={}, envKey={}, id={}, 占用后团队配额={}/{}",
                    team, envKey, env.getId(), repository.activeCount(team), properties.getTeamQuota());

            try {
                String endpoint = infrastructure.provision(team, envKey);
                transition(env, EnvStatus.READY);
                env.setEndpoint(endpoint);
                log.info("环境就绪 id={}, envKey={}, 到期时间={}", env.getId(), envKey, env.getExpiresAt());
                return env;
            } catch (RuntimeException ex) {
                repository.removeAndRelease(env);
                throw ex;
            }
        } finally {
            lock.unlock();
        }
    }

    private EphemeralEnvironment handleExistingOnApply(EphemeralEnvironment existing) {
        return switch (existing.getStatus()) {
            case READY -> {
                if (isExpired(existing)) {
                    throw new BusinessException(ErrorCode.ENV_EXPIRED,
                            "环境 %s 已到期，等待自动回收完成后可重新申请".formatted(existing.getId()));
                }
                log.info("幂等命中：返回已存在的可用环境 id={}", existing.getId());
                yield existing;
            }
            case REQUESTING -> throw new BusinessException(ErrorCode.ENV_PROVISION_CONFLICT,
                    "环境正在创建中（申请中），存在并发申请，本次请求未重复创建 id=%s".formatted(existing.getId()));
            case RELEASING -> throw new BusinessException(ErrorCode.ENV_RELEASING,
                    "环境正在回收中，不能重复申请 id=%s".formatted(existing.getId()));
            case RECLAIMED -> throw new BusinessException(ErrorCode.ENV_RECLAIMED,
                    "环境已回收 id=%s".formatted(existing.getId()));
        };
    }

    /**
     * 续租。仅属主团队、仅 READY 且未到期的环境可以续租，到期时间在原截止时间上顺延。
     */
    public EphemeralEnvironment renew(String id, String team, Long extendSeconds) {
        validateText(team, "team");
        validateText(id, "id");
        EphemeralEnvironment env = requireOwned(id, team);
        ReentrantLock lock = keyedLocks.lockFor(env.getTeam(), env.getEnvKey());
        lock.lock();
        try {
            switch (env.getStatus()) {
                case READY -> {
                    if (isExpired(env)) {
                        throw new BusinessException(ErrorCode.ENV_EXPIRED,
                                "环境 %s 已到期，不能续租".formatted(id));
                    }
                    long extend = extendSeconds == null ? properties.getRenewTtlSeconds() : extendSeconds;
                    if (extend <= 0) {
                        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "extendSeconds 必须为正数");
                    }
                    env.setExpiresAt(env.getExpiresAt().plusSeconds(extend));
                    log.info("环境续租成功 id={}, 新到期时间={}", id, env.getExpiresAt());
                    return env;
                }
                case RELEASING -> throw new BusinessException(ErrorCode.ENV_RELEASING,
                        "环境正在回收中，续租被拒绝 id=%s".formatted(id));
                case RECLAIMED -> throw new BusinessException(ErrorCode.ENV_RECLAIMED,
                        "环境已回收，不能续租 id=%s".formatted(id));
                case REQUESTING -> throw new BusinessException(ErrorCode.ILLEGAL_TRANSITION,
                        "环境尚在申请中，仅就绪(READY)环境可以续租 id=%s".formatted(id));
            }
            throw new IllegalStateException("unreachable");
        } finally {
            lock.unlock();
        }
    }

    /**
     * 主动释放（手动回收）。仅属主团队可操作；属主重复释放是幂等的。
     */
    public EphemeralEnvironment release(String id, String team) {
        validateText(team, "team");
        validateText(id, "id");
        EphemeralEnvironment env = requireOwned(id, team);
        ReentrantLock lock = keyedLocks.lockFor(env.getTeam(), env.getEnvKey());
        lock.lock();
        try {
            if (env.getStatus() == EnvStatus.RELEASING) {
                log.info("释放请求幂等命中：环境已在回收中 id={}", id);
                return env;
            }
            doReclaim(env, "手动释放");
            return env;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 查询环境。回收中/已到期的环境不会再以“可用环境”的形式返回；
     * 不属于本团队的环境明确拒绝，而不是静默查无此环境。
     */
    public EphemeralEnvironment get(String id, String team) {
        validateText(team, "team");
        validateText(id, "id");
        EphemeralEnvironment env = repository.findById(id);
        if (env == null) {
            throw new BusinessException(ErrorCode.ENV_NOT_FOUND, "环境不存在或已回收 id=%s".formatted(id));
        }
        if (!team.equals(env.getTeam())) {
            throw new BusinessException(ErrorCode.TEAM_MISMATCH,
                    "环境 %s 属于其他团队，禁止跨团队访问".formatted(id));
        }
        return switch (env.getStatus()) {
            case RELEASING -> throw new BusinessException(ErrorCode.ENV_RELEASING,
                    "环境正在回收中，不再可用 id=%s".formatted(id));
            case READY -> {
                if (isExpired(env)) {
                    throw new BusinessException(ErrorCode.ENV_EXPIRED,
                            "环境已到期，等待回收，不再可用 id=%s".formatted(id));
                }
                yield env;
            }
            default -> env;
        };
    }

    public List<EphemeralEnvironment> list(String team) {
        validateText(team, "team");
        return repository.findAll().stream()
                .filter(env -> team.equals(env.getTeam()))
                .toList();
    }

    /**
     * 到期回收：把已过截止时间的 READY 环境回收掉。
     * 一旦进入 RELEASING，后续续租/查询都会被明确拒绝。
     */
    public int reclaimExpired() {
        int reclaimed = 0;
        for (EphemeralEnvironment env : repository.findAll()) {
            if (env.getStatus() != EnvStatus.READY || !isExpired(env)) {
                continue;
            }
            ReentrantLock lock = keyedLocks.lockFor(env.getTeam(), env.getEnvKey());
            lock.lock();
            try {
                if (env.getStatus() == EnvStatus.READY && isExpired(env)) {
                    doReclaim(env, "到期自动回收");
                    reclaimed++;
                }
            } finally {
                lock.unlock();
            }
        }
        return reclaimed;
    }

    /**
     * 在持有环境锁的前提下执行 RELEASING -&gt; 模拟销毁 -&gt; RECLAIMED，并释放团队配额。
     */
    private void doReclaim(EphemeralEnvironment env, String reason) {
        transition(env, EnvStatus.RELEASING);
        log.info("开始回收 id={}, envKey={}, reason={}", env.getId(), env.getEnvKey(), reason);
        try {
            if (env.getEndpoint() != null) {
                infrastructure.teardown(env.getEndpoint());
            }
            transition(env, EnvStatus.RECLAIMED);
        } finally {
            // 即使销毁异常也要保证状态终态与配额释放不泄漏；销毁异常时记录并继续完成终态
            if (env.getStatus() != EnvStatus.RECLAIMED) {
                env.setStatus(EnvStatus.RECLAIMED);
            }
            repository.removeAndRelease(env);
        }
        log.info("回收完成 id={}, envKey={}, 释放后团队配额占用={}",
                env.getId(), env.getEnvKey(), repository.activeCount(env.getTeam()));
    }

    private void transition(EphemeralEnvironment env, EnvStatus target) {
        EnvStatus current = env.getStatus();
        if (!current.canTransitionTo(target)) {
            throw new BusinessException(ErrorCode.ILLEGAL_TRANSITION,
                    "非法状态流转：%s 不能从 %s 流转到 %s（id=%s）"
                            .formatted(env.getEnvKey(), current, target, env.getId()));
        }
        env.setStatus(target);
    }

    private EphemeralEnvironment requireOwned(String id, String team) {
        EphemeralEnvironment env = repository.findById(id);
        if (env == null) {
            throw new BusinessException(ErrorCode.ENV_NOT_FOUND, "环境不存在或已回收 id=%s".formatted(id));
        }
        if (!team.equals(env.getTeam())) {
            throw new BusinessException(ErrorCode.TEAM_MISMATCH,
                    "环境 %s 属于团队 %s，团队 %s 无权操作".formatted(id, env.getTeam(), team));
        }
        return env;
    }

    private boolean isExpired(EphemeralEnvironment env) {
        return !Instant.now(clock).isBefore(env.getExpiresAt());
    }

    private static void validateIdentity(String team, String envKey) {
        validateText(team, "team");
        validateText(envKey, "envKey");
    }

    private static void validateText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, field + " 不能为空");
        }
    }
}
