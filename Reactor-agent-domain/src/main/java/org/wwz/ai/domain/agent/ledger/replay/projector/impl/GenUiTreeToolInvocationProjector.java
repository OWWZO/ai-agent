package org.wwz.ai.domain.agent.ledger.replay.projector.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.ledger.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.GenUiTreeToolOutput;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.runtime.tool.common.canvas.CanvasToolNames;
import org.wwz.ai.domain.agent.runtime.tool.common.canvas.GenUiSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * emit_ui_tree projector.
 */
/**
 * UI tree 工具的历史回放投影器，恢复生成式 UI 树及其前端渲染事件。
 */
public class GenUiTreeToolInvocationProjector extends AbstractToolInvocationProjector {

    @Override
    public boolean supports(String toolName) {
        return CanvasToolNames.EMIT_UI_TREE.equals(toolName);
    }

    @Override
    public List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                              List<ArtifactView> artifacts,
                                              EventResult state) {
        GenUiTreeToolOutput output = invocation != null
                && invocation.getStructuredOutput() instanceof GenUiTreeToolOutput structured
                ? structured
                : null;

        Map<String, Object> tree = output == null ? null : output.getTree();
        String canvasId = output == null ? null : output.getCanvasId();
        if (tree == null && invocation != null) {
            tree = recoverTree(invocation.getInputJson());
        }

        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("isFinal", true);
        if (tree != null) {
            resultMap.put("tree", tree);
        }
        if (StringUtils.isNotBlank(canvasId)) {
            resultMap.put("canvas_id", canvasId);
        }
        putToolBindingIfPresent(resultMap, invocation);

        return List.of(buildTaskEvent(
                state,
                invocation,
                "ui_tree",
                buildStructuredToolResponse(invocation, "ui_tree", resultMap),
                buildArtifactRefs(artifacts)
        ));
    }

    private Map<String, Object> recoverTree(String inputJson) {
        if (StringUtils.isBlank(inputJson)) {
            return null;
        }
        try {
            Object parsed = JSON.parse(inputJson);
            if (!(parsed instanceof JSONObject obj)) {
                return null;
            }
            Object tree = obj.get("tree");
            if (tree == null) {
                tree = obj;
            }
            return GenUiSchema.validateUiTree(tree);
        } catch (Exception ignore) {
            return null;
        }
    }
}
