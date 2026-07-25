package org.wwz.ai.domain.agent.runtime.prompt;

/**
 * PlanSolve 主代理编排约定。
 * PlanSolve 请求启动时已自动进入 Plan Mode（对标 cchaha /plan），未批准前禁止实现。
 */
public final class PlanSolvePrompt {

    public static final String ORCHESTRATION_MARKER = "PLAN_SOLVE_ORCHESTRATION_V2";

    public static final String ORCHESTRATION = """
            # Plan-Execute 主代理职责 (%s)
            - 你是本会话的**主代理**（PlanSolve）：先规划、等人批、再实现与最终回复。
            - **本请求已自动进入 Plan Mode**（等同 cchaha 用户开启 plan）。在用户批准前：
              - MUST NOT 修改业务代码/配置/数据（仅可写 `.reactor/plan.md`）
              - MUST NOT 用执行类工具落地实现；Agent 仅 Explore 只读
              - 写好计划后调用 ExitPlanMode，等待用户批准
            - 纯问答且无需改系统：可直接自然语言回答，仍禁止写文件。
            - 用户批准后：用 TaskCreate / TodoWrite 维护清单，再 Agent(general-purpose) 或本机工具实现。
            - 同一轮可并行多个只读工具 / Explore Agent。
            - 子代理从零上下文启动：prompt 须写全目标与交付格式；只回结论。
            - 全部完成后：**不要再调用工具**，直接输出面向用户的最终回复（USER_FACING_REPLY_CONTRACT）。
            - 不要自批计划；ExitPlanMode 会挂起等人。
            """.formatted(ORCHESTRATION_MARKER);

    private PlanSolvePrompt() {
    }

    public static String ensureOrchestration(String systemPrompt) {
        String base = systemPrompt == null ? "" : systemPrompt.replace("\r\n", "\n").replace('\r', '\n');
        if (base.contains(ORCHESTRATION_MARKER)) {
            return base;
        }
        // 去掉 V1 编排块标记后的重复追加由 V2 marker 控制
        String block = ORCHESTRATION.trim();
        if (base.isBlank()) {
            return block + "\n";
        }
        return base.trim() + "\n\n" + block + "\n";
    }
}
