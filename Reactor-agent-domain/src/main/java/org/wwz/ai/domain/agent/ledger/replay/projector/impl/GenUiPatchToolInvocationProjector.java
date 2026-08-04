package org.wwz.ai.domain.agent.ledger.replay.projector.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.ledger.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.GenUiPatchToolOutput;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.runtime.tool.common.canvas.CanvasToolNames;
import org.wwz.ai.domain.agent.runtime.tool.common.canvas.GenUiSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * emit_ui_patch projector.
 */
/**
 * UI patch 工具的历史回放投影器，恢复对既有画布节点的增量修改事件。
 */
public class GenUiPatchToolInvocationProjector extends AbstractToolInvocationProjector {

    @Override
    public boolean supports(String toolName) {
        return CanvasToolNames.EMIT_UI_PATCH.equals(toolName);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                              List<ArtifactView> artifacts,
                                              EventResult state) {
        GenUiPatchToolOutput output = invocation != null
                && invocation.getStructuredOutput() instanceof GenUiPatchToolOutput structured
                ? structured
                : null;

        List<Map<String, Object>> patches = output == null ? null : output.getPatches();
        String canvasId = output == null ? null : output.getCanvasId();
        Integer seq = output == null ? null : output.getSeq();
        if ((patches == null || patches.isEmpty()) && invocation != null) {
            Map<String, Object> recovered = recoverPatch(invocation.getInputJson());
            if (recovered != null) {
                patches = (List<Map<String, Object>>) recovered.get("patches");
                if (canvasId == null && recovered.get("canvas_id") != null) {
                    canvasId = String.valueOf(recovered.get("canvas_id"));
                }
                if (seq == null && recovered.get("seq") instanceof Number n) {
                    seq = n.intValue();
                }
            }
        }

        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("isFinal", true);
        if (patches != null) {
            resultMap.put("patches", patches);
        }
        if (StringUtils.isNotBlank(canvasId)) {
            resultMap.put("canvas_id", canvasId);
        }
        if (seq != null) {
            resultMap.put("seq", seq);
        }
        putToolBindingIfPresent(resultMap, invocation);

        return List.of(buildTaskEvent(
                state,
                invocation,
                "ui_patch",
                buildStructuredToolResponse(invocation, "ui_patch", resultMap),
                buildArtifactRefs(artifacts)
        ));
    }

    private Map<String, Object> recoverPatch(String inputJson) {
        if (StringUtils.isBlank(inputJson)) {
            return null;
        }
        try {
            Object parsed = JSON.parse(inputJson);
            if (!(parsed instanceof JSONObject obj)) {
                return null;
            }
            return GenUiSchema.validateUiPatch(obj);
        } catch (Exception ignore) {
            return null;
        }
    }
}
