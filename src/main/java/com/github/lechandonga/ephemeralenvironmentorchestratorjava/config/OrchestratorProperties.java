package com.github.lechandonga.ephemeralenvironmentorchestratorjava.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 编排服务的可调参数，全部带默认值，本地开箱即跑。
 */
@ConfigurationProperties(prefix = "orchestrator")
public class OrchestratorProperties {

    /** 每个团队可同时占用的环境数量上限（REQUESTING/READY/RELEASING 均计入）。 */
    private int teamQuota = 3;

    /** 申请时默认的存活秒数。 */
    private long defaultTtlSeconds = 300;

    /** 一次续租延长的秒数。 */
    private long renewTtlSeconds = 300;

    /** 模拟基础设施创建环境的耗时（毫秒），用于本地模拟，不接真实集群。 */
    private long provisionDelayMillis = 30;

    /** 到期回收扫描间隔（毫秒）。 */
    private long reaperIntervalMillis = 500;

    public int getTeamQuota() {
        return teamQuota;
    }

    public void setTeamQuota(int teamQuota) {
        this.teamQuota = teamQuota;
    }

    public long getDefaultTtlSeconds() {
        return defaultTtlSeconds;
    }

    public void setDefaultTtlSeconds(long defaultTtlSeconds) {
        this.defaultTtlSeconds = defaultTtlSeconds;
    }

    public long getRenewTtlSeconds() {
        return renewTtlSeconds;
    }

    public void setRenewTtlSeconds(long renewTtlSeconds) {
        this.renewTtlSeconds = renewTtlSeconds;
    }

    public long getProvisionDelayMillis() {
        return provisionDelayMillis;
    }

    public void setProvisionDelayMillis(long provisionDelayMillis) {
        this.provisionDelayMillis = provisionDelayMillis;
    }

    public long getReaperIntervalMillis() {
        return reaperIntervalMillis;
    }

    public void setReaperIntervalMillis(long reaperIntervalMillis) {
        this.reaperIntervalMillis = reaperIntervalMillis;
    }
}
