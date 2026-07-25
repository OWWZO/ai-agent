package org.wwz.ai.domain.agent.runtime.tool.common.planmode;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionTaskItem;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionTaskListPublisher;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 更新 Todo 任务（对标 cc-haha TaskUpdateTool）。
 */
@Slf4j
@Data
public class TaskUpdateTool implements BaseTool {

    private AgentContext agentContext;

    @Override
    public String getName() {
        return TaskToolNames.TASK_UPDATE;
    }

    @Override
    public String getDescription() {
        return "更新任务：status（pending|in_progress|completed）、subject、description、activeForm、owner。"
                + "开工时设 in_progress，完成时设 completed。"
                + "可选 addBlocks / addBlockedBy 维护依赖。"
                + "status=deleted 可删除任务。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("taskId", Map.of("type", "string", "description", "任务 ID"));
        properties.put("subject", Map.of("type", "string", "description", "可选，新标题"));
        properties.put("description", Map.of("type", "string", "description", "可选，新描述"));
        properties.put("activeForm", Map.of("type", "string", "description", "可选，进行时文案"));
        properties.put("status", Map.of(
                "type", "string",
                "description", "可选：pending | in_progress | completed | deleted"));
        properties.put("owner", Map.of("type", "string", "description", "可选，负责人"));
        Map<String, Object> arr = new LinkedHashMap<>();
        arr.put("type", "array");
        arr.put("items", Map.of("type", "string"));
        arr.put("description", "可选，本任务阻塞的 taskId 列表");
        properties.put("addBlocks", arr);
        Map<String, Object> arr2 = new LinkedHashMap<>();
        arr2.put("type", "array");
        arr2.put("items", Map.of("type", "string"));
        arr2.put("description", "可选，阻塞本任务的 taskId 列表");
        properties.put("addBlockedBy", arr2);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("taskId"));
        return parameters;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        try {
            Map<String, Object> params = coerceMap(input);
            String taskId = trim(params.get("taskId"));
            if (StringUtils.isBlank(taskId)) {
                return fail("TaskUpdate 失败：taskId 必填");
            }
            if (agentContext == null) {
                return fail("TaskUpdate 失败：无 AgentContext");
            }
            String status = trim(params.get("status"));
            if ("deleted".equalsIgnoreCase(status)) {
                boolean removed = agentContext.requireSessionTaskList().delete(taskId);
                if (!removed) {
                    return ToolResultPayload.text("Task not found: " + taskId);
                }
                SessionTaskListPublisher.publish(agentContext);
                return ToolResultPayload.text("已删除任务 #" + taskId);
            }

            List<String> addBlocks = asStringList(params.get("addBlocks"));
            List<String> addBlockedBy = asStringList(params.get("addBlockedBy"));
            Optional<SessionTaskItem> updated = agentContext.requireSessionTaskList().update(
                    taskId,
                    emptyToNull(trim(params.get("subject"))),
                    params.containsKey("description") ? String.valueOf(params.get("description")) : null,
                    StringUtils.isBlank(status) ? null : status,
                    params.containsKey("activeForm") ? String.valueOf(params.get("activeForm")) : null,
                    params.containsKey("owner") ? String.valueOf(params.get("owner")) : null,
                    addBlocks,
                    addBlockedBy);
            if (updated.isEmpty()) {
                return ToolResultPayload.text("Task not found: " + taskId);
            }
            SessionTaskItem item = updated.get();
            SessionTaskListPublisher.publish(agentContext);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("task", item.toDetailMap());
            body.put("success", true);
            return ToolResultPayload.text(
                    "已更新任务 #" + item.getId() + " — " + item.getSubject()
                            + "\nstatus=" + item.getStatus() + "\n"
                            + JSON.toJSONString(body));
        } catch (Exception e) {
            log.warn("TaskUpdate failed", e);
            String msg = "TaskUpdate 失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return ToolResultPayload.failure(msg, msg, null, e.getMessage());
        }
    }

    private static ToolResultPayload fail(String msg) {
        return ToolResultPayload.failure(msg, msg, null, msg);
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null && StringUtils.isNotBlank(String.valueOf(item))) {
                result.add(String.valueOf(item).trim());
            }
        }
        return result.isEmpty() ? null : result;
    }

    private static String emptyToNull(String value) {
        return StringUtils.isBlank(value) ? null : value;
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
