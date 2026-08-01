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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 创建 Todo 任务（对标 cc-haha TaskCreateTool）。
 * 与 TaskStop 的后台运行任务无关。
 */
@Slf4j
@Data
public class TaskCreateTool implements BaseTool {

    private AgentContext agentContext;

    @Override
    public String getName() {
        return TaskToolNames.TASK_CREATE;
    }

    @Override
    public String getDescription() {
        return "在任务列表中创建新任务。用于复杂多步工作、plan mode 跟踪实现、或用户明确要求 todo list。"
                + "简单单步/纯对话不要用。subject 用祈使短句，description 写清要做什么。"
                + "新建状态固定 pending。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> subject = new LinkedHashMap<>();
        subject.put("type", "string");
        subject.put("description", "任务短标题（祈使句），如“实现登录接口”");

        Map<String, Object> description = new LinkedHashMap<>();
        description.put("type", "string");
        description.put("description", "任务详情：要做什么、验收标准");

        Map<String, Object> activeForm = new LinkedHashMap<>();
        activeForm.put("type", "string");
        activeForm.put("description", "可选，进行时文案（如“正在写测试”）");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("subject", subject);
        properties.put("description", description);
        properties.put("activeForm", activeForm);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("subject", "description"));
        return parameters;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        try {
            Map<String, Object> params = coerceMap(input);
            String subject = trim(params.get("subject"));
            String description = trim(params.get("description"));
            String activeForm = trim(params.get("activeForm"));
            if (StringUtils.isBlank(subject) || StringUtils.isBlank(description)) {
                return ToolResultPayload.failure(
                        "TaskCreate 失败：subject 与 description 必填",
                        "TaskCreate 失败：subject 与 description 必填",
                        null,
                        "missing fields");
            }
            if (agentContext == null) {
                return ToolResultPayload.failure("TaskCreate 失败：无 AgentContext", "TaskCreate 失败：无 AgentContext", null, "no context");
            }
            SessionTaskItem item = agentContext.requireSessionTaskList()
                    .create(subject, description, activeForm, null);
            SessionTaskListPublisher.publish(agentContext);
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("message", "已创建任务 #" + item.getId() + "：" + item.getSubject());
            fields.put("task", item.toSummaryMap());
            return ToolResultPayload.okData(TaskToolNames.TASK_CREATE, fields);
        } catch (Exception e) {
            log.warn("TaskCreate failed", e);
            String msg = "TaskCreate 失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
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

    private static String trim(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
