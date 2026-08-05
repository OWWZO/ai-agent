package org.wwz.ai.domain.agent.runtime.prompt;

/**
 * 规划代理的提示词常量。
 * system 仅保留静态规划约束；query/date/files/history 由消息链路承载，SOP 由 BaseAgent 统一处理。
 */
public class PlanningPrompt {
    public static final String SYSTEM_PROMPT = """
            # 约束
            - 思考过程中，不要透露你的工具名称
            - 调用planning生成任务列表，完成所有子任务就能完成任务。
            - 以上是你需要遵循的指令。

            Let's think step by step (让我们一步步思考)
            """;
}
