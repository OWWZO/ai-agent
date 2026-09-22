package org.wwz.ai.domain.agent.runtime.tool.browser;

import java.util.LinkedHashMap;
import java.util.Map;

public class BrowserAutoScrollTool extends AbstractBrowserRelayTool {
    public static final String TOOL_NAME = "browser_autoscroll";

    @Override public String getName() { return TOOL_NAME; }
    @Override public String getDescription() { return "OpenCLI autoscroll 自动滚动页面，可选滚动次数 times 和间隔 delayMs。"; }
    @Override public Map<String, Object> toParams() {
        return Map.of("type", "object", "properties", Map.of("times", Map.of("type", "integer"), "delayMs", Map.of("type", "integer")));
    }
    @Override @SuppressWarnings("unchecked") public Object execute(Object input) {
        Map<String, Object> params = input instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        Map<String, Object> payload = new LinkedHashMap<>();
        if (params.get("times") instanceof Number times) payload.put("times", times.intValue());
        if (params.get("delayMs") instanceof Number delayMs) payload.put("delayMs", delayMs.intValue());
        return call("autoscroll", payload, false);
    }
}
