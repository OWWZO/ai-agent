package org.wwz.ai.domain.agent.runtime.tool.browser;

import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BrowserFillTool extends AbstractBrowserRelayTool {

    public static final String TOOL_NAME = "browser_fill";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "清空并填充 input/textarea。ref 可以是 snapshot 索引（如 e14 或 14）或 CSS selector；CSS 多匹配时可用 0-based nth。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("ref", Map.of("type", "string", "description", "snapshot 索引如 e14/14，或 CSS selector"));
        properties.put("text", Map.of("type", "string"));
        properties.put("nth", Map.of("type", "integer", "description", "CSS 多匹配时选择第几个，0-based"));
        return Map.of("type", "object", "properties", properties, "required", List.of("ref", "text"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        Map<String, Object> params = input instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        if (params.get("ref") == null || params.get("text") == null) {
            return ToolResultPayload.failure("ref/text 不能为空", "ref/text 不能为空", null, "missing fields");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ref", String.valueOf(params.get("ref")).trim());
        payload.put("text", String.valueOf(params.get("text")));
        if (params.get("nth") instanceof Number nth) payload.put("nth", nth.intValue());
        return call("fill", payload, false);
    }
}
