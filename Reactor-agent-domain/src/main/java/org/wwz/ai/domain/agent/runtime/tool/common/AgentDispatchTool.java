package org.wwz.ai.domain.agent.runtime.tool.common;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinition;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRegistry;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentResult;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRunner;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主 Agent 派发同步子 Agent 的工具入口（对标 cc-haha AgentTool 同步路径）。
 * 输入：description / prompt / subagent_type
 * 输出：status=completed 的结论文本，中间工具过程不进入主上下文。
 */
@Slf4j
@Data
public class AgentDispatchTool implements BaseTool {

    public static final String NAME = "Agent";

    private final SubAgentRunner subAgentRunner;
    private final SubAgentRegistry subAgentRegistry;
    private AgentContext agentContext;

    public AgentDispatchTool(SubAgentRunner subAgentRunner, SubAgentRegistry subAgentRegistry) {
        this.subAgentRunner = subAgentRunner;
        this.subAgentRegistry = subAgentRegistry;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("派发一个同步子 Agent 执行独立任务，阻塞等待完成后返回精简报告。")
                .append("子 Agent 从零上下文开始，请在 prompt 中写全背景与交付要求。")
                .append("可用 subagent_type：");
        List<String> lines = new ArrayList<>();
        if (subAgentRegistry != null) {
            for (SubAgentDefinition def : subAgentRegistry.list()) {
                lines.add(def.getAgentType() + " — " + def.getWhenToUse());
            }
        }
        if (lines.isEmpty()) {
            sb.append("general-purpose, Explore");
        } else {
            sb.append(String.join("; ", lines));
        }
        sb.append("。省略 subagent_type 时默认 general-purpose。不要用本工具做简单单次查询。");
        return sb.toString();
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("type", "string");
        description.put("description", "任务短描述，3-5 个词，用于展示与日志");

        Map<String, Object> prompt = new LinkedHashMap<>();
        prompt.put("type", "string");
        prompt.put("description", "交给子 Agent 的完整任务说明。子 Agent 看不到主对话，需包含目标、已知信息、范围与输出格式");

        Map<String, Object> subagentType = new LinkedHashMap<>();
        subagentType.put("type", "string");
        String typeHint = "子 Agent 类型；省略则 general-purpose。可用: "
                + (subAgentRegistry == null || subAgentRegistry.listTypeNames().isEmpty()
                ? "Explore, general-purpose"
                : String.join(", ", subAgentRegistry.listTypeNames()));
        subagentType.put("description", typeHint);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("description", description);
        properties.put("prompt", prompt);
        properties.put("subagent_type", subagentType);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("description", "prompt"));
        return parameters;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        try {
            Map<String, Object> params = input instanceof Map
                    ? (Map<String, Object>) input
                    : JSON.parseObject(JSON.toJSONString(input), Map.class);
            if (params == null) {
                return ToolResultPayload.failure("Agent 执行失败：参数为空", "Agent 执行失败：参数为空", null, "empty input");
            }

            String description = trimToString(params.get("description"));
            String prompt = trimToString(params.get("prompt"));
            String subagentType = trimToString(params.get("subagent_type"));
            // Plan Mode：空白或 general-purpose 强制 Explore（只读）
            if (agentContext != null
                    && agentContext.getPlanModeState() != null
                    && agentContext.getPlanModeState().isPlanMode()) {
                if (StringUtils.isBlank(subagentType)
                        || SubAgentRegistry.TYPE_GENERAL_PURPOSE.equals(subagentType)) {
                    subagentType = SubAgentRegistry.TYPE_EXPLORE;
                }
            }

            if (StringUtils.isBlank(prompt)) {
                return ToolResultPayload.failure(
                        "Agent 执行失败：prompt 不能为空",
                        "Agent 执行失败：prompt 不能为空",
                        null,
                        "prompt blank");
            }
            if (StringUtils.isBlank(description)) {
                description = StringUtils.defaultIfBlank(subagentType, "subagent-task");
            }
            if (subAgentRunner == null) {
                return ToolResultPayload.failure(
                        "Agent 执行失败：SubAgentRunner 未注入",
                        "Agent 执行失败：SubAgentRunner 未注入",
                        null,
                        "runner missing");
            }

            SubAgentResult result = subAgentRunner.run(agentContext, description, prompt, subagentType);
            String observation = formatObservation(result);
            if (!result.isCompleted()) {
                return ToolResultPayload.failure(observation, observation, null, result.getErrorMsg());
            }
            return ToolResultPayload.text(observation);
        } catch (Exception e) {
            log.error("Agent dispatch tool failed", e);
            String msg = "Agent 执行失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return ToolResultPayload.failure(msg, msg, null, e.getMessage());
        }
    }

    private static String formatObservation(SubAgentResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", result.getStatus());
        body.put("agentId", result.getAgentId());
        body.put("agentType", result.getAgentType());
        body.put("description", result.getDescription());
        body.put("content", result.getContent());
        body.put("totalToolUseCount", result.getTotalToolUseCount());
        body.put("totalDurationMs", result.getTotalDurationMs());
        if (StringUtils.isNotBlank(result.getErrorMsg())) {
            body.put("errorMsg", result.getErrorMsg());
        }
        // 主 Agent 优先读 content；附带元数据便于调试
        if (result.isCompleted() && StringUtils.isNotBlank(result.getContent())) {
            return "status=completed\n"
                    + "agentType=" + result.getAgentType() + "\n"
                    + "agentId=" + result.getAgentId() + "\n"
                    + "totalToolUseCount=" + result.getTotalToolUseCount() + "\n"
                    + "totalDurationMs=" + result.getTotalDurationMs() + "\n\n"
                    + result.getContent();
        }
        return JSON.toJSONString(body);
    }

    private static String trimToString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
