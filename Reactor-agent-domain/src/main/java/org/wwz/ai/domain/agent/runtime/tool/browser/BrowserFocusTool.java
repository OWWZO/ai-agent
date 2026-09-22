package org.wwz.ai.domain.agent.runtime.tool.browser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BrowserFocusTool extends AbstractBrowserRelayTool {
    public static final String TOOL_NAME = "browser_focus";

    @Override public String getName() { return TOOL_NAME; }
    @Override public String getDescription() { return "OpenCLI focus 元素；ref 为数字/eN 或 CSS selector，可用 nth 指定匹配项。"; }
    @Override public Map<String, Object> toParams() {
        return Map.of("type", "object", "properties", Map.of("ref", Map.of("type", "string"), "nth", Map.of("type", "integer")), "required", List.of("ref"));
    }
    @Override @SuppressWarnings("unchecked") public Object execute(Object input) {
        Map<String, Object> params = input instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        Map<String, Object> payload = new LinkedHashMap<>();
        if (params.get("ref") != null) payload.put("ref", String.valueOf(params.get("ref")).trim());
        if (params.get("nth") instanceof Number nth) payload.put("nth", nth.intValue());
        return call("focus", payload, false);
    }
}
