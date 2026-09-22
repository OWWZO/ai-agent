package org.wwz.ai.domain.agent.runtime.tool.browser;

import java.util.LinkedHashMap;
import java.util.Map;

public class BrowserExtractTool extends AbstractBrowserRelayTool {
    public static final String TOOL_NAME = "browser_extract";

    @Override public String getName() { return TOOL_NAME; }
    @Override public String getDescription() { return "OpenCLI extract 提取页面内容，可选 CSS selector 缩小范围。"; }
    @Override public Map<String, Object> toParams() {
        return Map.of("type", "object", "properties", Map.of("selector", Map.of("type", "string")));
    }
    @Override @SuppressWarnings("unchecked") public Object execute(Object input) {
        Map<String, Object> params = input instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        Map<String, Object> payload = new LinkedHashMap<>();
        if (params.get("selector") != null) payload.put("selector", String.valueOf(params.get("selector")));
        return call("extract", payload, false);
    }
}
