package org.wwz.ai.domain.agent.ledger.replay.projector.impl;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamAccumulator;
import org.wwz.ai.domain.agent.ledger.model.replay.ProjectedReplayEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认 fallback projector。
 */
/**
 * 未注册专用 projector 的工具回放兜底实现，保证未知工具仍能生成基础事件。
 */
public class DefaultToolInvocationProjector extends AbstractToolInvocationProjector {

    @Override
    public boolean supports(String toolName) {
        return false;
    }

    @Override
    public List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                              List<ArtifactView> artifacts,
                                              AgentStreamAccumulator state) {
        // 历史行 tool_result 为 NULL，回落到 llm_oberserve（旧数据那里存的就是完整 JSON）。
        // 失败时两列都被 serializeFailure 填成「Error: xxx」，errorMsg 因 defaultIfBlank 永远轮不上；
        // 这里显式按状态分流，保证错误码（ERR-xxx）不被 Error 前缀文本顶掉。
        String text = invocation == null ? "" : resolveResultText(invocation);

        Map<String, Object> toolResult = new LinkedHashMap<>();
        toolResult.put("toolName", invocation == null ? null : invocation.getToolName());
        toolResult.put("toolParam", invocation == null ? Map.of() : readMap(invocation.getInputJson()));
        toolResult.put("toolResult", text);

        return List.of(buildTaskEvent(
                state,
                invocation,
                "tool_result",
                buildToolResultResponse(invocation, toolResult),
                buildArtifactRefs(artifacts)
        ));
    }

    /**
     * 失败优先展示 errorMsg（结构化错误码比 Error 前缀文本更有诊断价值），
     * 成功才取完整结果 JSON；历史行 tool_result 为 NULL 时回落到 llm_oberserve。
     */
    private static String resolveResultText(ToolInvocationView invocation) {
        boolean failed = invocation.getStatus() != null
                && invocation.getStatus() != ExecutionLedgerConstants.STATUS_SUCCESS;
        if (failed) {
            return StringUtils.defaultIfBlank(invocation.getErrorMsg(),
                    StringUtils.defaultIfBlank(invocation.getToolResult(), invocation.getLlmObservation()));
        }
        return StringUtils.defaultIfBlank(invocation.getToolResult(), invocation.getLlmObservation());
    }
}
