package org.wwz.ai.domain.agent.runtime.tool.common.planmode;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionTaskItem;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 按 ID 获取 Todo 任务详情（对标 cc-haha TaskGetTool）。
 */
@Slf4j
@Data
public class TaskGetTool implements BaseTool {

    private AgentContext agentContext;

    @Override
    public String getName() {
        return TaskToolNames.TASK_GET;
    }

    @Override
    public String getDescription() {
        return "按 taskId 获取任务完整详情（description、status、依赖）。"
                + "开工前应读取 description 与 blockedBy；列表摘要请用其它列表能力，本工具用于单任务深读。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> taskId = new LinkedHashMap<>();
        taskId.put("type", "string");
        taskId.put("description", "任务 ID（TaskCreate 返回的 id）");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("taskId", taskId);

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
                return ToolResultPayload.failure("TaskGet 失败：taskId 必填", "TaskGet 失败：taskId 必填", null, "missing taskId");
            }
            if (agentContext == null) {
                return ToolResultPayload.failure("TaskGet 失败：无 AgentContext", "TaskGet 失败：无 AgentContext", null, "no context");
            }
            Optional<SessionTaskItem> found = agentContext.requireSessionTaskList().get(taskId);
            if (found.isEmpty()) {
                return ToolResultPayload.text("Task not found: " + taskId);
            }
            SessionTaskItem item = found.get();
            StringBuilder sb = new StringBuilder();
            sb.append("Task #").append(item.getId()).append(" — ").append(item.getSubject()).append('\n');
            sb.append("Status: ").append(item.getStatus()).append('\n');
            sb.append("Description: ").append(item.getDescription()).append('\n');
            if (item.getBlockedBy() != null && !item.getBlockedBy().isEmpty()) {
                sb.append("Blocked by: ").append(String.join(", ", item.getBlockedBy())).append('\n');
            }
            if (item.getBlocks() != null && !item.getBlocks().isEmpty()) {
                sb.append("Blocks: ").append(String.join(", ", item.getBlocks())).append('\n');
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("task", item.toDetailMap());
            return ToolResultPayload.text(sb + JSON.toJSONString(body));
        } catch (Exception e) {
            log.warn("TaskGet failed", e);
            String msg = "TaskGet 失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
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
}
