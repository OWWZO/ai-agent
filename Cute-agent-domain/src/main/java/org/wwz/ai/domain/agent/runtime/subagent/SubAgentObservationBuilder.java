package org.wwz.ai.domain.agent.runtime.subagent;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 子 Agent 结果的观察载荷构建。
 * <p>
 * 前台派发（AgentDispatchTool）与后台结算（BackgroundSubAgentExecutor）此前各自
 * 私藏一份几乎相同的 buildObservationData；字段顺序与判空规则一旦漂移，
 * 同一条子 Agent 结果在两条路径上会给主 Agent 不同的 observation。
 */
public final class SubAgentObservationBuilder {

    private SubAgentObservationBuilder() {
    }

    /**
     * @param toolName         回执上的 tool 名（前台为 Agent 工具名，后台沿用父调用的 tool 名）
     * @param background       是否为后台派发：后台回执额外标记 run_in_background
     */
    public static Map<String, Object> build(SubAgentResult result, String toolName, boolean background) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tool", toolName);
        body.put("ok", result.isCompleted());
        body.put("status", result.getStatus());
        body.put("agentId", result.getAgentId());
        body.put("agentType", result.getAgentType());
        if (StringUtils.isNotBlank(result.getChildSessionId())) {
            body.put("childSessionId", result.getChildSessionId());
        }
        body.put("description", result.getDescription());
        body.put("content", result.getContent());
        body.put("totalToolUseCount", result.getTotalToolUseCount());
        body.put("totalDurationMs", result.getTotalDurationMs());
        if (background) {
            body.put("run_in_background", true);
        }
        if (result.getMemoryPersisted() != null) {
            body.put("memoryPersisted", result.getMemoryPersisted());
        }
        if (StringUtils.isNotBlank(result.getErrorMsg())) {
            body.put("errorMsg", result.getErrorMsg());
        }
        return body;
    }
}
