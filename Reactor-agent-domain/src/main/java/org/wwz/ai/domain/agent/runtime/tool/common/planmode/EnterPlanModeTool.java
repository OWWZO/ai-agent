package org.wwz.ai.domain.agent.runtime.tool.common.planmode;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.planmode.PlanArtifactStore;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModePromptInjector;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModeState;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 进入 Plan Mode（对标 cc-haha EnterPlanModeTool）。
 * 仅主 Agent 使用；子 Agent 工具池剔除。
 */
@Slf4j
@Data
public class EnterPlanModeTool implements BaseTool {

    private AgentContext agentContext;
    private PlanArtifactStore planArtifactStore;

    public EnterPlanModeTool() {
    }

    public EnterPlanModeTool(PlanArtifactStore planArtifactStore) {
        this.planArtifactStore = planArtifactStore;
    }

    @Override
    public String getName() {
        return TaskToolNames.ENTER_PLAN_MODE;
    }

    @Override
    public String getDescription() {
        return "请求进入 plan mode，用于需探索与设计的复杂任务："
                + "新功能、多方案权衡、架构改动、多文件重构、需求不清。"
                + "简单修复/明确指令/纯调研不要进入。"
                + "进入后应只读探索、写计划到 .reactor/plan.md；实现前用 ExitPlanMode 请求用户批准。"
                + "本工具无参数。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", Collections.emptyMap());
        parameters.put("required", Collections.emptyList());
        return parameters;
    }

    @Override
    public Object execute(Object input) {
        try {
            if (agentContext == null) {
                return ToolResultPayload.failure(
                        "EnterPlanMode 失败：无 AgentContext",
                        "EnterPlanMode 失败：无 AgentContext",
                        null,
                        "no context");
            }
            if (agentContext.getRequestId() != null && agentContext.getRequestId().contains(":sub:")) {
                return ToolResultPayload.failure(
                        "EnterPlanMode 不能在子 Agent 上下文中使用",
                        "EnterPlanMode tool cannot be used in agent contexts",
                        null,
                        "subagent");
            }
            PlanModeState state = agentContext.requirePlanModeState();
            if (state.isPlanMode()) {
                Map<String, Object> already = new LinkedHashMap<>();
                already.put("alreadyInPlanMode", Boolean.TRUE);
                already.put("message", "Already in plan mode. Continue exploring and writing the plan to .reactor/plan.md.");
                already.put("planFilePath", PlanArtifactStore.RELATIVE_PLAN_PATH);
                return ToolResultPayload.okData(TaskToolNames.ENTER_PLAN_MODE, already);
            }
            state.enterPlanMode();

            String planPathHint = PlanArtifactStore.RELATIVE_PLAN_PATH;
            if (planArtifactStore != null) {
                var path = planArtifactStore.resolvePlanPath(agentContext.getSessionId());
                if (path != null) {
                    planPathHint = path.toString();
                    state.setPlan(state.getPlanContent(), planPathHint);
                }
            }

            if (agentContext.getPrinter() != null) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("mode", PlanModeState.MODE_PLAN);
                payload.put("planFilePath", planPathHint);
                agentContext.getPrinter().send("plan_mode_entered", payload);
            }

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("entered", Boolean.TRUE);
            fields.put("message", "Entered plan mode. Write the plan to .reactor/plan.md, then call ExitPlanMode for approval.");
            fields.put("planFilePath", planPathHint);
            fields.put("instructions", PlanModePromptInjector.PLAN_MODE_INSTRUCTIONS.trim());
            return ToolResultPayload.okData(TaskToolNames.ENTER_PLAN_MODE, fields);
        } catch (Exception e) {
            log.warn("EnterPlanMode failed", e);
            String msg = "EnterPlanMode 失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return ToolResultPayload.failureFrom(msg, null);
        }
    }
}
