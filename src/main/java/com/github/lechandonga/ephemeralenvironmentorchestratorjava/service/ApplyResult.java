package com.github.lechandonga.ephemeralenvironmentorchestratorjava.service;

import com.github.lechandonga.ephemeralenvironmentorchestratorjava.model.EphemeralEnvironment;

/**
 * 申请结果。idempotentHit=true 表示命中幂等，返回的是已存在的同一环境，
 * 本次请求没有创建任何新资源、也没有重复占用配额。
 */
public record ApplyResult(EphemeralEnvironment environment, boolean idempotentHit) {
}
