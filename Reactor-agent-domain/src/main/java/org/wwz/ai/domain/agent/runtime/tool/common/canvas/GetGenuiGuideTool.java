package org.wwz.ai.domain.agent.runtime.tool.common.canvas;

import lombok.Data;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GenUI 指南查询工具。
 * <p>只读返回组件树协议、布局约束和常见反模式，帮助模型在生成复杂 UI 前建立正确结构。</p>
 */
@Data
public class GetGenuiGuideTool implements BaseTool {

    private AgentContext agentContext;

    @Override
    public String getName() {
        return CanvasToolNames.GET_GENUI_GUIDE;
    }

    @Override
    public String getDescription() {
        return "Return the GenUI guide: wire format, workflow_order, layout, anti-patterns. "
                + "Call before non-trivial emit_ui_tree (dashboards, multi-card UIs). "
                + "Then call list_ui_components. Read-only.";
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
        return ToolResultPayload.fromData(GenUiGuidePayload.payload());
    }
}
