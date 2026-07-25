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
 * 停止后台运行任务（对标 cc-haha TaskStopTool）。
 * 操作 RuntimeBackgroundTaskRegistry，不是 Todo 列表。
 * 仅主 Agent 应挂载；子 Agent 工具池会剔除本工具。
 */
@Slf4j
@Data
public class TaskStopTool implements BaseTool {

    private AgentContext agentContext;

    @Override
    public String getName() {
        return TaskToolNames.TASK_STOP;
    }

    @Override
    public String getDescription() {
        return "按 ID 停止正在运行的后台任务（shell/agent 等）。"
                + "仅在用户明确要求取消/停止，或任务 runaway/有害时使用。"
                + "不要仅因已读够输出就 stop agent。task_id 与 shell_id 二选一。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> taskId = new LinkedHashMap<>();
        taskId.put("type", "string");
        taskId.put("description", "后台任务 ID");

        Map<String, Object> shellId = new LinkedHashMap<>();
        shellId.put("type", "string");
        shellId.put("description", "兼容旧名 shell_id，等同 task_id");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("task_id", taskId);
        properties.put("shell_id", shellId);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        // task_id / shell_id 二选一，在 execute 内校验；required 显式为空数组
        parameters.put("required", java.util.Collections.emptyList());
        return parameters;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        try {
            Map<String, Object> params = coerceMap(input);
            String id = firstNonBlank(trim(params.get("task_id")), trim(params.get("shell_id")));
            if (StringUtils.isBlank(id)) {
                return ToolResultPayload.failure(
                        "TaskStop 失败：需要 task_id 或 shell_id",
                        "TaskStop 失败：需要 task_id 或 shell_id",
                        null,
                        "missing id");
            }
            if (agentContext == null) {
                return ToolResultPayload.failure("TaskStop 失败：无 AgentContext", "TaskStop 失败：无 AgentContext", null, "no context");
            }
            Optional<RuntimeBackgroundTask> existing = agentContext.requireBackgroundTasks().get(id);
            if (existing.isEmpty()) {
                return ToolResultPayload.failure(
                        "TaskStop 失败：任务不存在 " + id,
                        "Task not found: " + id,
                        null,
                        "not_found");
            }
            RuntimeBackgroundTask before = existing.get();
            if (!RuntimeBackgroundTask.STATUS_RUNNING.equals(before.getStatus())) {
                return ToolResultPayload.failure(
                        "TaskStop 失败：任务非 running 状态（" + before.getStatus() + "）",
                        "Task not running: " + id + " status=" + before.getStatus(),
                        null,
                        "not_running");
            }
            RuntimeBackgroundTask stopped = agentContext.requireBackgroundTasks().stop(id).orElse(before);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "Stopped task " + id);
            body.put("task_id", stopped.getId());
            body.put("task_type", stopped.getType());
            if (StringUtils.isNotBlank(stopped.getCommand())) {
                body.put("command", stopped.getCommand());
            }
            return ToolResultPayload.text(
                    "已停止后台任务 " + id + "（type=" + stopped.getType() + "）\n" + JSON.toJSONString(body));
        } catch (Exception e) {
            log.warn("TaskStop failed", e);
            String msg = "TaskStop 失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return ToolResultPayload.failure(msg, msg, null, e.getMessage());
        }
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

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.isNotBlank(a)) {
            return a;
        }
        return b;
    }
}
