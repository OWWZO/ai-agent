package org.wwz.ai.domain.agent.runtime.tool.common.planmode;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionTaskItem;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 列出全部 Todo 任务。
 */
@Slf4j
@Data
public class TaskListTool implements BaseTool {

    private AgentContext agentContext;

    @Override
    public String getName() {
        return TaskToolNames.TASK_LIST;
    }

    @Override
    public String getDescription() {
        return "列出当前会话全部任务的摘要（id、subject、status、blockedBy）。"
                + "无参数。需要详情时用 TaskGet。";
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
                return ToolResultPayload.failure("TaskList 失败：无 AgentContext", "TaskList 失败：无 AgentContext", null, "no context");
            }
            List<SessionTaskItem> all = agentContext.requireSessionTaskList().list();
            if (all.isEmpty()) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("tasks", List.of());
                empty.put("message", "No tasks found");
                return ToolResultPayload.okData(TaskToolNames.TASK_LIST, empty);
            }
            Set<String> completedIds = all.stream()
                    .filter(t -> SessionTaskItem.STATUS_COMPLETED.equals(t.getStatus()))
                    .map(SessionTaskItem::getId)
                    .collect(Collectors.toSet());

            List<Map<String, Object>> rows = new ArrayList<>();
            for (SessionTaskItem item : all) {
                List<String> blockedBy = item.getBlockedBy() == null
                        ? List.of()
                        : item.getBlockedBy().stream()
                        .filter(id -> !completedIds.contains(id))
                        .toList();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.getId());
                row.put("subject", item.getSubject());
                row.put("status", item.getStatus());
                row.put("owner", item.getOwner());
                row.put("blockedBy", blockedBy);
                rows.add(row);
            }
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("tasks", rows);
            return ToolResultPayload.okData(TaskToolNames.TASK_LIST, fields);
        } catch (Exception e) {
            log.warn("TaskList failed", e);
            String msg = "TaskList 失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return ToolResultPayload.failureFrom(msg, null);
        }
    }
}
