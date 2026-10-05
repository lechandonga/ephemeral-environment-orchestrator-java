package com.github.lechandonga.ephemeralenvironmentorchestratorjava.model;

import java.time.Instant;

/**
 * 一次性临时环境。同一个 team 下 envName 唯一。
 */
public class EphemeralEnvironment {

    private final String id;
    private final String team;
    private final String envName;

    private volatile EnvState state;
    private volatile Instant expiresAt;
    private final Instant createdAt;

    public EphemeralEnvironment(String id, String team, String envName, Instant createdAt) {
        this.id = id;
        this.team = team;
        this.envName = envName;
        this.state = EnvState.REQUESTING;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getTeam() {
        return team;
    }

    public String getEnvName() {
        return envName;
    }

    public EnvState getState() {
        return state;
    }

    public void setState(EnvState state) {
        this.state = state;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
