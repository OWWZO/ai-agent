package org.wwz.ai.domain.agent.runtime.tool.common.canvas;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Return on-demand HTML canvas authoring guide.
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
        return "Return the on-demand professional HTML guide for canvas_publish(mode=html): "
                + "design method, visual quality, accessibility, preview runtime "
                + "(Tailwind/Inter/wa-* shell, JS enabled), Reactor delivery (inline vs html_path), "
                + "and a structural template. Charts/KPI/dashboards → emit_ui_tree instead. "
                + "Use for substantial or appearance-sensitive webpages; skip for trivial HTML. "
                + "Read-only, no side effects.";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", Collections.emptyMap());
        parameters.put("additionalProperties", false);
        return parameters;
    }

    @Override
    public Object execute(Object input) {
        Map<String, Object> guide = HtmlCanvasGuidePayload.payload();
        String json = JSON.toJSONString(guide);
        return ToolResultPayload.text(json);
    }
}
