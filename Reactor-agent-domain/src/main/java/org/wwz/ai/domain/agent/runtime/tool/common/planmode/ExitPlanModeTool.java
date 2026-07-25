package org.wwz.ai.domain.agent.runtime.tool.common.planmode;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.planmode.PendingPlanApproval;
import org.wwz.ai.domain.agent.runtime.planmode.PendingPlanApprovalRegistry;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalDecision;
import org.wwz.ai.domain.agent.runtime.planmode.PlanArtifactStore;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModePromptInjector;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModeState;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * 退出 Plan Mode 并提交计划供用户批准（对标 cc-haha ExitPlanModeV2Tool）。
 * <p>
 * 不自批：SSE 推送 plan_approval 卡片 → 工具线程 await → 用户 approve/reject 后返回。
 * </p>
 */
@Slf4j
@Data
public class ExitPlanModeTool implements BaseTool {

    private AgentContext agentContext;
    private PendingPlanApprovalRegistry approvalRegistry;
    private PlanArtifactStore planArtifactStore;

    public ExitPlanModeTool(PendingPlanApprovalRegistry approvalRegistry, PlanArtifactStore planArtifactStore) {
        this.approvalRegistry = approvalRegistry;
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
                + "调用后会挂起直到用户批准或拒绝。";
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
        // 全部可选；显式 required=[] 避免 ToolSchemaNormalizer 告警
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
            if (approvalRegistry == null) {
                return fail("ExitPlanMode 失败：PendingPlanApprovalRegistry 未注入");
            }
            PlanModeState state = agentContext.requirePlanModeState();
            if (!state.isPlanMode()) {
                return fail("ExitPlanMode 失败：当前不在 plan mode");
            }

            Map<String, Object> params = coerceMap(input);
            String planFromArg = trim(params.get("plan"));
            String planFilePathArg = trim(params.get("planFilePath"));

            // 优先：参数 plan → 磁盘 plan → state.planContent
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

            PendingPlanApproval pending = approvalRegistry.create(
                    agentContext.getSessionId(),
                    agentContext.getRequestId(),
                    toolCallId,
                    planContent,
                    planFilePath,
                    null);

            // SSE：前端展示计划批准卡片
            if (agentContext.getPrinter() != null) {
                agentContext.getPrinter().send(
                        pending.getApprovalId(),
                        "plan_approval",
                        pending.toClientPayload(),
                        false);
            }

            PlanApprovalDecision decision;
            try {
                decision = approvalRegistry.awaitDecision(pending);
            } catch (TimeoutException e) {
                state.clearPendingApproval();
                String msg = "用户在时限内未批准计划（timeout=" + pending.getTimeoutMs()
                        + "ms）。仍停留在 plan mode，可修改计划后再次 ExitPlanMode。";
                return ToolResultPayload.failure(msg, msg, null, "timeout");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                state.clearPendingApproval();
                return fail("等待用户批准被中断");
            } catch (RuntimeException e) {
                state.clearPendingApproval();
                return fail("等待用户批准失败：" + e.getMessage());
            }

            if (decision != null && decision.isApproved()) {
                String finalPlan = StringUtils.isNotBlank(decision.getEditedPlanContent())
                        ? decision.getEditedPlanContent()
                        : planContent;
                if (planArtifactStore != null) {
                    planArtifactStore.writePlan(agentContext.getSessionId(), finalPlan)
                            .ifPresent(p -> state.setPlan(finalPlan, p));
                } else {
                    state.setPlan(finalPlan, planFilePath);
                }
                String restored = state.exitPlanMode();
                String text = PlanModePromptInjector.buildApprovedPlanToolResult(
                        finalPlan, state.getPlanFilePath(), restored);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("approved", true);
                body.put("restoredMode", restored);
                body.put("plan", finalPlan);
                body.put("filePath", state.getPlanFilePath());
                body.put("approvalId", pending.getApprovalId());
                return ToolResultPayload.text(text + "\n" + JSON.toJSONString(body));
            }

            // rejected — 留在 plan mode
            state.clearPendingApproval();
            String feedback = decision == null ? null : decision.getFeedback();
            String text = PlanModePromptInjector.buildRejectedPlanToolResult(feedback);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("approved", false);
            body.put("feedback", feedback);
            body.put("approvalId", pending.getApprovalId());
            body.put("stillInPlanMode", true);
            return ToolResultPayload.text(text + "\n" + JSON.toJSONString(body));
        } catch (Exception e) {
            log.warn("ExitPlanMode failed", e);
            return fail("ExitPlanMode 失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private static ToolResultPayload fail(String msg) {
        return ToolResultPayload.failure(msg, msg, null, msg);
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
