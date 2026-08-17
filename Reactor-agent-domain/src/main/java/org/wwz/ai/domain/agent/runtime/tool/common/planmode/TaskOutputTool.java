package org.wwz.ai.domain.agent.runtime.tool.common.planmode;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTask;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 读取后台任务输出（对标 cc-haha TaskOutputTool）。
 * 可阻塞等待终态；与 Todo 列表无关。
 */
@Slf4j
@Data
public class TaskOutputTool implements BaseTool {

    private static final long DEFAULT_TIMEOUT_MS = 30_000L;
    private static final long MAX_TIMEOUT_MS = 600_000L;

    private AgentContext agentContext;

    @Override
    public String getName() {
        return TaskToolNames.TASK_OUTPUT;
    }

    @Override
    public String getDescription() {
        return "获取后台任务（Agent run_in_background / shell 等）的输出。"
                + "block=true（默认）时等待完成或超时；timeout 为毫秒，默认 30000，最大 600000。"
                + "task_id 来自 Agent 后台派发返回值。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> taskId = new LinkedHashMap<>();
        taskId.put("type", "string");
        taskId.put("description", "后台任务 ID");

        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "boolean");
        block.put("description", "是否等待任务结束，默认 true");

        Map<String, Object> timeout = new LinkedHashMap<>();
        timeout.put("type", "integer");
        timeout.put("description", "最大等待毫秒数，默认 30000，最大 600000");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("task_id", taskId);
        properties.put("block", block);
        properties.put("timeout", timeout);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", java.util.List.of("task_id"));
        return parameters;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        try {
            Map<String, Object> params = coerceMap(input);
            String taskId = trim(params.get("task_id"));
            if (StringUtils.isBlank(taskId)) {
                return ToolResultPayload.failure(
                        "TaskOutput 失败：task_id 必填",
                        "TaskOutput 失败：task_id 必填",
                        null,
                        "missing task_id");
            }
            if (agentContext == null) {
                return ToolResultPayload.failure(
                        "TaskOutput 失败：无 AgentContext",
                        "TaskOutput 失败：无 AgentContext",
                        null,
                        "no context");
            }

            boolean shouldBlock = params.containsKey("block")
                    ? coerceBoolean(params.get("block"), true)
                    : true;
            long timeoutMs = coerceTimeout(params.get("timeout"));

            Optional<RuntimeBackgroundTask> found = agentContext.requireBackgroundTasks().get(taskId);
            if (found.isEmpty()) {
                String sid = agentContext.getSessionId() != null
                        ? agentContext.getSessionId()
                        : agentContext.getRequestId();
                return ToolResultPayload.failure(
                        "TaskOutput 失败：任务不存在 " + taskId
                                + "（session=" + sid
                                + "；若为跨轮查询请确认已执行 migration_session_tasklist.sql 且任务在同 session 下创建）",
                        "Task not found: " + taskId + " session=" + sid,
                        null,
                        "not_found");
            }

            RuntimeBackgroundTask task = found.get();
            String retrieval = "success";
            if (shouldBlock && RuntimeBackgroundTask.STATUS_RUNNING.equals(task.getStatus())) {
                task = agentContext.requireBackgroundTasks()
                        .awaitTerminal(taskId, timeoutMs)
                        .orElse(task);
                if (RuntimeBackgroundTask.STATUS_RUNNING.equals(task.getStatus())) {
                    retrieval = "timeout";
                }
            } else if (RuntimeBackgroundTask.STATUS_RUNNING.equals(task.getStatus())) {
                retrieval = "not_ready";
            }

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("retrieval_status", retrieval);
            fields.put("task_id", task.getId());
            fields.put("task_type", task.getType());
            fields.put("status", task.getStatus());
            fields.put("description", task.getDescription());
            if (StringUtils.isNotBlank(task.getAgentId())) {
                fields.put("agentId", task.getAgentId());
            }
            if (StringUtils.isNotBlank(task.getAgentType())) {
                fields.put("agentType", task.getAgentType());
            }
            if (StringUtils.isNotBlank(task.getOutput())) {
                fields.put("output", task.getOutput());
                fields.put("result", task.getOutput());
            }
            if (StringUtils.isNotBlank(task.getErrorMsg())) {
                fields.put("error", task.getErrorMsg());
            }
            if (task.getTotalToolUseCount() != null) {
                fields.put("totalToolUseCount", task.getTotalToolUseCount());
            }
            if (task.getTotalDurationMs() != null) {
                fields.put("totalDurationMs", task.getTotalDurationMs());
            }
            if (task.getEndedAtMs() != null) {
                fields.put("endedAtMs", task.getEndedAtMs());
            }
            fields.put("message", buildMessage(retrieval, task));
            return ToolResultPayload.okData(TaskToolNames.TASK_OUTPUT, fields);
        } catch (Exception e) {
            log.warn("TaskOutput failed", e);
            String msg = "TaskOutput 失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return ToolResultPayload.failureFrom(msg, null);
        }
    }

    private static String buildMessage(String retrieval, RuntimeBackgroundTask task) {
        if ("timeout".equals(retrieval)) {
            return "任务仍在运行，等待超时。可再次 TaskOutput 或 TaskStop。";
        }
        if ("not_ready".equals(retrieval)) {
            return "任务仍在运行（未阻塞等待）。";
        }
        if (RuntimeBackgroundTask.STATUS_COMPLETED.equals(task.getStatus())) {
            return "任务已完成。";
        }
        if (RuntimeBackgroundTask.STATUS_STOPPED.equals(task.getStatus())) {
            return "任务已停止。";
        }
        if (RuntimeBackgroundTask.STATUS_FAILED.equals(task.getStatus())) {
            return "任务失败：" + StringUtils.defaultString(task.getErrorMsg());
        }
        return "ok";
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

    private static boolean coerceBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim().toLowerCase();
        if ("true".equals(text) || "1".equals(text) || "yes".equals(text)) {
            return true;
        }
        if ("false".equals(text) || "0".equals(text) || "no".equals(text)) {
            return false;
        }
        return defaultValue;
    }

    private static long coerceTimeout(Object value) {
        if (value == null) {
            return DEFAULT_TIMEOUT_MS;
        }
        long parsed;
        if (value instanceof Number number) {
            parsed = number.longValue();
        } else {
            try {
                parsed = Long.parseLong(String.valueOf(value).trim());
            } catch (NumberFormatException e) {
                return DEFAULT_TIMEOUT_MS;
            }
        }
        if (parsed < 0) {
            return 0;
        }
        return Math.min(parsed, MAX_TIMEOUT_MS);
    }
}
