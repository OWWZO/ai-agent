package org.wwz.ai.domain.agent.runtime.tool.browser;

import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BrowserHoverTool extends AbstractBrowserRelayTool {

    public static final String TOOL_NAME = "browser_hover";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "悬停在元素上（如打开菜单/tooltip）。ref 可以是 snapshot 索引（如 e14 或 14）或 CSS selector；CSS 多匹配时可用 0-based nth。";
    }

    @Override
    public Map<String, Object> toParams() {
        return Map.of("type", "object", "properties", Map.of("ref", Map.of("type", "string", "description", "snapshot 索引如 e14/14，或 CSS selector"), "nth", Map.of("type", "integer", "description", "CSS 多匹配时选择第几个，0-based")), "required", List.of("ref"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        Map<String, Object> params = input instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        if (params.get("ref") == null) {
            return ToolResultPayload.failure("ref 不能为空", "ref 不能为空", null, "missing ref");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ref", String.valueOf(params.get("ref")).trim());
        if (params.get("nth") instanceof Number nth) payload.put("nth", nth.intValue());
        return call("hover", payload, false);
    }
}
