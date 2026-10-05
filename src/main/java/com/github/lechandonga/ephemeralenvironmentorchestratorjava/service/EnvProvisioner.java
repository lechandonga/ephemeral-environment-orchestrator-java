package com.github.lechandonga.ephemeralenvironmentorchestratorjava.service;

import com.github.lechandonga.ephemeralenvironmentorchestratorjava.model.EphemeralEnvironment;

/**
 * 外部依赖（真实集群 / 云资源）的抽象。本地运行时只使用模拟实现，
 * 不接触任何真实集群或云服务。
 */
public interface EnvProvisioner {

    /** 申请并准备一个环境（模拟拉起资源）。 */
    void provision(EphemeralEnvironment env);

    /** 回收并销毁环境（模拟释放资源）。 */
    void teardown(EphemeralEnvironment env);
}
