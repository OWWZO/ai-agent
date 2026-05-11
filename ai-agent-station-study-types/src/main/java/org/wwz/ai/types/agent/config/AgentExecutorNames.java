package org.wwz.ai.types.agent.config;

/**
 * Agent 主链路命名执行器常量。
 */
public final class AgentExecutorNames {

    public static final String DISPATCH_EXECUTOR = "agentDispatchExecutor";
    public static final String LLM_EXECUTOR = "agentLlmExecutor";
    public static final String TASK_EXECUTOR = "agentTaskExecutor";
    public static final String TOOL_EXECUTOR = "agentToolExecutor";
    public static final String HEARTBEAT_SCHEDULER = "agentHeartbeatScheduler";

    private AgentExecutorNames() {
    }
}
