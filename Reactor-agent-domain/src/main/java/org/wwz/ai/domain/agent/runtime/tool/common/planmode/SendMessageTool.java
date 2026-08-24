package org.wwz.ai.domain.agent.runtime.tool.common.planmode;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.cancel.PendingInjectMessage;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTask;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionAgentMailboxHub;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 主 Agent → 运行中子 Agent 的指导消息。
 * 仅后台/仍在跑的子 Agent 可投递；同步阻塞派发期间主 Agent 无法调用本工具。
 */
@Slf4j
@Data
public class SendMessageTool implements BaseTool {

    private AgentContext agentContext;

    @Override
    public String getName() {
        return TaskToolNames.SEND_MESSAGE;
    }

    @Override
    public String getDescription() {
        return "向正在运行的后台子 Agent 发送中途指导（下一 step 可见）。"
                + " to 填 agentId 或 task_id（后台 Agent 返回值）。"
                + " 仅 run_in_background 的子 Agent 可收信；已结束请用 Agent(resume_agent_id=…)。"
                + " 同步阻塞 Agent 调用期间主 Agent 无法发信。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> to = new LinkedHashMap<>();
        to.put("type", "string");
        to.put("description", "目标 agentId 或后台 task_id");

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "string");
        message.put("description", "指导内容，子 Agent 下一步会看到");

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("type", "string");
        summary.put("description", "可选，5-10 字摘要（展示用）");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("to", to);
        properties.put("message", message);
        properties.put("summary", summary);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("to", "message"));
        return parameters;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        try {
            if (agentContext == null) {
                return ToolResultPayload.failure(
                        "SendMessage 失败：无 AgentContext",
                        "SendMessage 失败：无 AgentContext",
                        null,
                        "no context");
            }
            Map<String, Object> params = coerceMap(input);
            String to = trim(params.get("to"));
            String message = trim(params.get("message"));
            String summary = trim(params.get("summary"));
            if (StringUtils.isBlank(to) || StringUtils.isBlank(message)) {
                return ToolResultPayload.failure(
                        "SendMessage 失败：to 与 message 必填",
                        "SendMessage 失败：to 与 message 必填",
                        null,
                        "missing fields");
            }

            String sessionId = StringUtils.defaultIfBlank(agentContext.getSessionId(), agentContext.getRequestId());
            ResolvedTarget target = resolveTarget(sessionId, to);
            if (target == null || StringUtils.isBlank(target.agentId)) {
                return ToolResultPayload.softFailData(TaskToolNames.SEND_MESSAGE, Map.of(
                        "ok", false,
                        "to", to,
                        "message", "无法解析目标 agentId/task_id。请使用后台 Agent 返回的 agentId 或 task_id。",
                        "hint", "Agent(run_in_background=true) 后使用返回的 agentId/task_id"
                ));
            }

            if (!SessionAgentMailboxHub.isActive(sessionId, target.agentId)
                    && !isBackgroundRunning(target)) {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("ok", false);
                fields.put("to", to);
                fields.put("agentId", target.agentId);
                if (StringUtils.isNotBlank(target.taskId)) {
                    fields.put("task_id", target.taskId);
                }
                fields.put("message", "目标子 Agent 未在运行，无法投递中途指导。");
                fields.put("hint", "请用 Agent(resume_agent_id=\"" + target.agentId
                        + "\", prompt=\"…\") 续跑并带上新指导。");
                return ToolResultPayload.softFailData(TaskToolNames.SEND_MESSAGE, fields);
            }

            PendingInjectMessage inject = PendingInjectMessage.builder()
                    .text(StringUtils.isNotBlank(summary)
                            ? ("[" + summary + "] " + message)
                            : message)
                    .source(PendingInjectMessage.SOURCE_COORDINATOR)
                    .createdAtMs(System.currentTimeMillis())
                    .build();
            int queued = SessionAgentMailboxHub.offer(sessionId, target.agentId, inject);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("ok", true);
            fields.put("to", to);
            fields.put("agentId", target.agentId);
            if (StringUtils.isNotBlank(target.taskId)) {
                fields.put("task_id", target.taskId);
            }
            fields.put("queued", queued);
            fields.put("message", "已投递指导，子 Agent 下一步可见。");
            return ToolResultPayload.okData(TaskToolNames.SEND_MESSAGE, fields);
        } catch (Exception e) {
            log.warn("SendMessage failed", e);
            String msg = "SendMessage 失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return ToolResultPayload.failureFrom(msg, null);
        }
    }

    private ResolvedTarget resolveTarget(String sessionId, String to) {
        // 先按 task_id 查后台任务
        Optional<RuntimeBackgroundTask> byTask = agentContext.requireBackgroundTasks().get(to);
        if (byTask.isPresent()) {
            RuntimeBackgroundTask task = byTask.get();
            return new ResolvedTarget(task.getAgentId(), task.getId(), task);
        }
        // 再在后台任务列表里按 agentId 找
        for (RuntimeBackgroundTask task : agentContext.requireBackgroundTasks().listAll()) {
            if (task != null && to.equals(task.getAgentId())) {
                return new ResolvedTarget(task.getAgentId(), task.getId(), task);
            }
        }
        // 直接当 agentId（mailbox 可能仍 active）
        if (SessionAgentMailboxHub.isActive(sessionId, to)) {
            return new ResolvedTarget(to, null, null);
        }
        return new ResolvedTarget(to, null, null);
    }

    private boolean isBackgroundRunning(ResolvedTarget target) {
        if (target.task != null) {
            return RuntimeBackgroundTask.STATUS_RUNNING.equals(target.task.getStatus());
        }
        if (StringUtils.isBlank(target.agentId)) {
            return false;
        }
        for (RuntimeBackgroundTask task : agentContext.requireBackgroundTasks().listRunning()) {
            if (target.agentId.equals(task.getAgentId())) {
                return true;
            }
        }
        return false;
    }

    private record ResolvedTarget(String agentId, String taskId, RuntimeBackgroundTask task) {
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
