package com.github.lechandonga.ephemeralenvironmentorchestratorjava.domain;

import java.time.Instant;

/**
 * 一个一次性临时环境的领域实体。
 */
public class EphemeralEnvironment {

    private final String id;
    private final String team;
    private final String envKey;
    private volatile EnvStatus status;
    private volatile Instant expiresAt;
    private volatile String endpoint;
    private volatile Instant createdAt;
    private volatile Instant updatedAt;

    public EphemeralEnvironment(String id, String team, String envKey, EnvStatus status,
                                Instant expiresAt, Instant now) {
        this.id = id;
        this.team = team;
        this.envKey = envKey;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public String getTeam() {
        return team;
    }

    public String getEnvKey() {
        return envKey;
    }

    public EnvStatus getStatus() {
        return status;
    }

    public void setStatus(EnvStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
        this.updatedAt = Instant.now();
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
