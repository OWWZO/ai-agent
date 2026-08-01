package org.wwz.ai.domain.agent.runtime.tool.common.canvas;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.GenUiTreeToolOutput;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validate and emit a GenUI tree to chat (inline) and workspace panel.
 */
@Slf4j
@Data
public class EmitUiTreeTool implements BaseTool {

    private AgentContext agentContext;

    @Override
    public String getName() {
        return CanvasToolNames.EMIT_UI_TREE;
    }

    @Override
    public String getDescription() {
        return "Emit a validated gen UI tree (schemaVersion 1) that renders inline in chat and in the side panel "
                + "with real React components (Chart=ECharts, ThreeJsFrame=procedural 3D, Model3D=glb/gltf). "
                + "PREFERRED path for charts, KPI tiles, dashboards, multi-card layouts, data tables, simple 3D. "
                + "Do NOT use canvas_publish for simple charts/3D shapes — use Chart / ThreeJsFrame instead. "
                + "Before non-trivial trees: get_genui_guide then list_ui_components. "
                + "Args: {tree, optional canvas_id}. tree may be object or JSON string. "
                + "Prefer emit_ui_patch for small updates.";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> tree = new HashMap<>();
        tree.put("description", "GenUI tree envelope or bare root node (object preferred; JSON string accepted).");

        Map<String, Object> canvasId = new HashMap<>();
        canvasId.put("type", "string");
        canvasId.put("description", "Optional canvas id for continuity.");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("tree", tree);
        properties.put("canvas_id", canvasId);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("tree"));
        return parameters;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        try {
            Map<String, Object> params = input instanceof Map<?, ?> map ? castMap(map) : Map.of();
            boolean salvaged = Boolean.TRUE.equals(params.get("__salvaged"));
            Object treeRaw = params.get("tree");
            if (treeRaw instanceof String s) {
                String raw = s.trim();
                if (raw.isEmpty()) {
                    return failure("tree string is empty");
                }
                try {
                    treeRaw = JSON.parse(raw);
                } catch (Exception e) {
                    return failure("tree is not valid JSON: " + e.getMessage());
                }
            }
            Map<String, Object> normalized = GenUiSchema.validateUiTree(treeRaw);
            String canvasId = stringVal(params.get("canvas_id"));

            Map<String, Object> streamPayload = new LinkedHashMap<>();
            streamPayload.put("tree", normalized);
            streamPayload.put("isFinal", true);
            if (canvasId != null) {
                streamPayload.put("canvas_id", canvasId);
            }
            ToolArtifactSource artifactSource = agentContext == null
                    ? null
                    : agentContext.getCurrentToolArtifactSource();
            if (artifactSource != null) {
                streamPayload.put("toolCallId", artifactSource.getToolCallId());
                streamPayload.put("toolName", artifactSource.getToolName());
            }
            if (salvaged) {
                streamPayload.put("salvaged", true);
            }

            if (agentContext != null && agentContext.getPrinter() != null) {
                String toolCallId = artifactSource == null ? null : artifactSource.getToolCallId();
                String digitalEmployee = agentContext.getToolCollection() == null
                        ? null
                        : agentContext.getToolCollection().getDigitalEmployee(getName());
                agentContext.getPrinter().send(toolCallId, "ui_tree", streamPayload, digitalEmployee, true);
            }

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("message", "GenUI tree emitted successfully"
                    + (salvaged ? " (args salvaged)" : "")
                    + ". Prefer emit_ui_patch for small updates.");
            fields.put("canvasId", canvasId);
            fields.put("salvaged", salvaged);
            fields.put("tree", normalized);
            return ToolResultPayload.okData(
                    getName(),
                    fields,
                    GenUiTreeToolOutput.builder()
                            .tree(normalized)
                            .canvasId(canvasId)
                            .salvaged(salvaged)
                            .build()
            );
        } catch (Exception e) {
            log.warn("emit_ui_tree failed: {}", e.getMessage());
            return failure("emit_ui_tree validation failed: " + e.getMessage());
        }
    }

    private ToolResultPayload failure(String message) {
        return ToolResultPayload.failureFrom(message, null);
    }

    private Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() != null) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return out;
    }

    private String stringVal(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return StringUtils.isBlank(s) ? null : s;
    }
}
