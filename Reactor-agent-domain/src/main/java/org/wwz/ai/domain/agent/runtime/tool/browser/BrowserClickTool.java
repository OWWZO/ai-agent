package org.wwz.ai.domain.agent.runtime.tool.browser;

import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BrowserClickTool extends AbstractBrowserRelayTool {

    public static final String TOOL_NAME = "browser_click";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "点击元素。ref 可以是 snapshot 索引（如 e14 或 14）或 CSS selector；CSS 多匹配时可用 nth（从 0 开始）。";
    }

    @Override
    public Map<String, Object> toParams() {
        return Map.of(
                "type", "object",
                "properties", Map.of("ref", Map.of("type", "string", "description", "snapshot 索引如 e14/14，或 CSS selector"), "nth", Map.of("type", "integer", "description", "CSS 多匹配时选择第几个，0-based")),
                "required", List.of("ref")
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        Map<String, Object> params = input instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        Object ref = params.get("ref");
        if (ref == null || String.valueOf(ref).isBlank()) {
            return ToolResultPayload.failure("ref 不能为空", "ref 不能为空", null, "missing ref");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ref", String.valueOf(ref).trim());
        if (params.get("nth") instanceof Number nth) payload.put("nth", nth.intValue());
        return call("click", payload, false);
    }
}
