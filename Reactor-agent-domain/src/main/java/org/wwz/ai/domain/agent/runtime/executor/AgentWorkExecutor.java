package org.wwz.ai.domain.agent.runtime.executor;

import java.util.concurrent.Executor;

/**
 * Agent 工作执行器契约。
 *
 * <p>业务层只依赖 Executor；额外的场景和请求标识仅用于装配层观测。</p>
 */
public interface AgentWorkExecutor extends Executor {

    void execute(Runnable command, String scene, String requestId);

    @Override
    default void execute(Runnable command) {
        execute(command, "unknown", null);
    }
}
