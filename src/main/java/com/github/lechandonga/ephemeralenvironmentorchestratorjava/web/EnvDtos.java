package com.github.lechandonga.ephemeralenvironmentorchestratorjava.web;

import com.github.lechandonga.ephemeralenvironmentorchestratorjava.model.EphemeralEnvironment;

import java.time.Duration;
import java.time.Instant;

public final class EnvDtos {

    private EnvDtos() {
    }

    public record ApplyRequest(String team, String envName, Duration ttl) {
    }

    public record RenewRequest(String team, Duration extendBy) {
    }

    public record ReleaseRequest(String team) {
    }

    public record EnvView(
            String id,
            String team,
            String envName,
            String state,
            Instant createdAt,
            Instant expiresAt) {

        public static EnvView from(EphemeralEnvironment env) {
            return new EnvView(
                    env.getId(),
                    env.getTeam(),
                    env.getEnvName(),
                    env.getState().name(),
                    env.getCreatedAt(),
                    env.getExpiresAt());
        }
    }

    public record ApplyResponse(EnvView environment, boolean idempotentHit) {
    }

    public record ErrorResponse(String code, String message, Instant timestamp) {
    }
}
