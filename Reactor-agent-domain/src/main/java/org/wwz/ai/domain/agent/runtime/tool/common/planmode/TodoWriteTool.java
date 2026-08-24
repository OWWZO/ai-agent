package org.wwz.ai.domain.agent.runtime.tool.common.planmode;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionTaskItem;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionTaskListPublisher;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionTaskListStore;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 整表写入 Todo 列表。
 * 与 TaskCreate/Update/List（V2 细粒度）并存：适合一次同步整份清单。
 * 全部 completed 时清空列表。
 */
@Slf4j
@Data
public class TodoWriteTool implements BaseTool {

    private AgentContext agentContext;

    @Override
    public String getName() {
        return TaskToolNames.TODO_WRITE;
    }

    @Override
    public String getDescription() {
        return "用完整 todo 列表覆盖当前会话任务清单。每项含 content（或 subject）、status（pending|in_progress|completed），"
                + "可选 activeForm。"
                + "复杂多步/计划获批后跟踪进度时使用；简单对话不要用。"
                + "若全部为 completed，列表会被清空。"
                + "细粒度增改也可用 TaskCreate / TaskUpdate。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> itemProps = new LinkedHashMap<>();
        itemProps.put("content", Map.of("type", "string", "description", "任务内容/标题"));
        itemProps.put("subject", Map.of("type", "string", "description", "可选，同 content"));
        itemProps.put("description", Map.of("type", "string", "description", "可选，详情"));
        itemProps.put("status", Map.of(
                "type", "string",
                "description", "pending | in_progress | completed"));
        itemProps.put("activeForm", Map.of("type", "string", "description", "可选，进行时文案"));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "object");
        item.put("properties", itemProps);
        item.put("required", List.of("status"));

        Map<String, Object> todos = new LinkedHashMap<>();
        todos.put("type", "array");
        todos.put("items", item);
        todos.put("description", "更新后的完整 todo 列表");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("todos", todos);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("todos"));
        return parameters;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        try {
            if (agentContext == null) {
                return ToolResultPayload.failure("TodoWrite 失败：无 AgentContext", "TodoWrite 失败：无 AgentContext", null, "no context");
            }
            Map<String, Object> params = coerceMap(input);
            Object rawTodos = params.get("todos");
            if (!(rawTodos instanceof List<?> list)) {
                return ToolResultPayload.failure("TodoWrite 失败：todos 必须为数组", "TodoWrite 失败：todos 必须为数组", null, "bad todos");
            }
            List<Map<String, Object>> todoMaps = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    todoMaps.add((Map<String, Object>) map);
                }
            }
            SessionTaskListStore store = agentContext.requireSessionTaskList();
            List<SessionTaskItem> oldTodos = store.replaceAll(todoMaps);
            List<SessionTaskItem> newTodos = store.list();
            SessionTaskListPublisher.publish(agentContext);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("message", "Todos have been modified successfully. Continue using the todo list to track progress.");
            fields.put("oldCount", oldTodos.size());
            fields.put("newCount", newTodos.size());
            fields.put("tasks", store.toClientTaskList());
            return ToolResultPayload.okData(TaskToolNames.TODO_WRITE, fields);
        } catch (Exception e) {
            log.warn("TodoWrite failed", e);
            String msg = "TodoWrite 失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return ToolResultPayload.failureFrom(msg, null);
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
}
