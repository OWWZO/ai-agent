package org.wwz.ai.domain.agent.runtime.tool.common.planmode;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalRequiredException;
import org.wwz.ai.domain.agent.runtime.planmode.PlanArtifactStore;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModeState;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 退出 Plan Mode 并提交计划供用户批准（对标 cc-haha ExitPlanModeV2Tool）。
 * <p>
 * Continuation 版：校验并落盘计划后抛出 {@link PlanApprovalRequiredException}，
 * 由上层持久化断点并结束 Run A；不再在工具线程上 Future.get 阻塞等待。
 * </p>
 */
@Slf4j
@Data
public class ExitPlanModeTool implements BaseTool {

    private AgentContext agentContext;
    private PlanArtifactStore planArtifactStore;

    public ExitPlanModeTool(PlanArtifactStore planArtifactStore) {
        this.planArtifactStore = planArtifactStore;
    }

    /** @deprecated 保留兼容旧装配签名；registry 已不再使用 */
    @Deprecated
    public ExitPlanModeTool(Object ignoredRegistry, PlanArtifactStore planArtifactStore) {
        this.planArtifactStore = planArtifactStore;
    }

    @Override
    public String getName() {
        return TaskToolNames.EXIT_PLAN_MODE;
    }

    @Override
    public String getDescription() {
        return "计划写完后调用，请求用户批准并退出 plan mode。"
                + "不要用 AskUserQuestion 问“计划可以吗？”——那是本工具职责。"
                + "研究/搜索类任务不要调用。可选 plan 参数提交计划正文；"
                + "也可事先写入 .reactor/plan.md。"
                + "调用后当前 run 会结束并等待用户批准；用户提交后由 continuation run 继续。"
                + "本轮必须是唯一 tool call。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("type", "string");
        plan.put("description", "可选，完整计划 Markdown 正文");

        Map<String, Object> planFilePath = new LinkedHashMap<>();
        planFilePath.put("type", "string");
        planFilePath.put("description", "可选，计划文件路径");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("plan", plan);
        properties.put("planFilePath", planFilePath);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", java.util.Collections.emptyList());
        return parameters;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        try {
            if (agentContext == null) {
                return fail("ExitPlanMode 失败：无 AgentContext");
            }
            PlanModeState state = agentContext.requirePlanModeState();
            if (!state.isPlanMode()) {
                return fail("ExitPlanMode 失败：当前不在 plan mode");
            }

            Map<String, Object> params = coerceMap(input);
            String planFromArg = trim(params.get("plan"));
            String planFilePathArg = trim(params.get("planFilePath"));

            String planContent = planFromArg;
            if (StringUtils.isBlank(planContent) && planArtifactStore != null) {
                planContent = planArtifactStore.readPlan(agentContext.getSessionId()).orElse(null);
            }
            if (StringUtils.isBlank(planContent)) {
                planContent = state.getPlanContent();
            }
            if (StringUtils.isBlank(planContent)) {
                return fail("ExitPlanMode 失败：计划正文为空。请先将计划写入 .reactor/plan.md 或在 plan 参数中提供正文。");
            }

            String planFilePath = planFilePathArg;
            if (planArtifactStore != null) {
                var written = planArtifactStore.writePlan(agentContext.getSessionId(), planContent);
                if (written.isPresent()) {
                    planFilePath = written.get();
                } else if (StringUtils.isBlank(planFilePath) && planArtifactStore.resolvePlanPath(agentContext.getSessionId()) != null) {
                    planFilePath = planArtifactStore.resolvePlanPath(agentContext.getSessionId()).toString();
                }
            }

            state.requestExitWithPlan(planContent, planFilePath);

            String toolCallId = null;
            ToolArtifactSource source = agentContext.getCurrentToolArtifactSource();
            if (source != null) {
                toolCallId = source.getToolCallId();
            }

            throw new PlanApprovalRequiredException(planContent, planFilePath, toolCallId);
        } catch (PlanApprovalRequiredException yield) {
            throw yield;
        } catch (Exception e) {
            log.warn("ExitPlanMode failed", e);
            return fail("ExitPlanMode 失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private static ToolResultPayload fail(String msg) {
        return ToolResultPayload.failureFrom(msg, null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> coerceMap(Object input) {
        if (input instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (input == null) {
            return Map.of();
        }
        return JSON.parseObject(JSON.toJSONString(input), Map.class);
    }

    private static String trim(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
