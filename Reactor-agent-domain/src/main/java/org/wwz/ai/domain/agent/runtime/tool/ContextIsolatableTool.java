package org.wwz.ai.domain.agent.runtime.tool;

import org.wwz.ai.domain.agent.runtime.agent.AgentContext;

/**
 * 显式声明「可按 AgentContext 隔离」的工具。
 * <p>
 * 并发架构约定见 {@link ToolIsolation}：
 * 子 Agent / 并行任务优先拿到独立实例；会话协作状态放在 {@link AgentContext}。
 * 无特殊构造依赖的工具也可由 {@link ToolIsolation} 反射 fork，不必都实现本接口。
 * 长流式工具（deep_search 等）建议实现本接口，并把执行态做成单次调用局部变量。
 */
public interface ContextIsolatableTool extends BaseTool {

    /**
     * 为指定 context 创建独立工具副本。
     *
     * @param context 子 Agent 或绑定目标 context，不可为 null
     * @return 绑定到该 context、且不共享执行态的新实例
     */
    BaseTool isolateFor(AgentContext context);
}
