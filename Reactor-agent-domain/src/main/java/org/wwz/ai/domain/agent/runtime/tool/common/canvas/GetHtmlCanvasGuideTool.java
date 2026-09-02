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
 * 按需返回 HTML canvas 编写指南的只读工具。
 *
 * 工具本身不生成页面，也不触碰工作区；它只把稳定的设计、运行时和交付边界
 * 暴露给 Agent，实际页面仍由后续 canvas_publish 调用负责生成。
 */
@Data
public class GetHtmlCanvasGuideTool implements BaseTool {

    private AgentContext agentContext;

    @Override
    public String getName() {
        return CanvasToolNames.GET_HTML_CANVAS_GUIDE;
    }

    @Override
    public String getDescription() {
        return "Return the on-demand professional HTML guide for canvas_publish: "
                + "design method, visual quality, accessibility, preview runtime for an existing workspace HTML file, "
                + "and Reactor delivery (workspace html_path only), "
                + "and a structural template. Charts/KPI/dashboards → emit_ui_tree instead. "
                + "Use for substantial or appearance-sensitive webpages after writing the HTML to workspace. "
                + "Read-only, no side effects.";
    }

    @Override
    public Map<String, Object> toParams() {
        // 指南工具没有输入参数，明确声明空对象 schema，避免模型误传业务字段。
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", Collections.emptyMap());
        parameters.put("required", List.of());
        parameters.put("additionalProperties", false);
        return parameters;
    }

    @Override
    public Object execute(Object input) {
        // 统一包装为工具结果，保持与其它运行时工具的返回协议一致。
        return ToolResultPayload.fromData(HtmlCanvasGuidePayload.payload());
    }
}
