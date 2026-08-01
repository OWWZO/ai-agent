package org.wwz.ai.domain.agent.runtime.tool.common.canvas;

import lombok.Data;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ListUiComponentsTool implements BaseTool {

    private AgentContext agentContext;

    @Override
    public String getName() {
        return CanvasToolNames.LIST_UI_COMPONENTS;
    }

    @Override
    public String getDescription() {
        return "Return the gen UI component catalog (kinds + prop hints) for the current subset. "
                + "Call get_genui_guide first for layout, then this tool before non-trivial emit_ui_tree. "
                + "Read-only.";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", Collections.emptyMap());
        parameters.put("required", List.of());
        parameters.put("additionalProperties", false);
        return parameters;
    }

    @Override
    public Object execute(Object input) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("node_shape", "{\"kind\":\"...\",\"props\":{...},\"children\":[...]}");
        fields.put("rules", List.of(
                "Every component prop goes inside props.",
                "children is only for nested nodes, never strings.",
                "Omit nodeId unless you need stable emit_ui_patch targets."
        ));
        fields.put("components", GenUiCatalog.listCatalog());
        return ToolResultPayload.okData(CanvasToolNames.LIST_UI_COMPONENTS, fields);
    }
}
