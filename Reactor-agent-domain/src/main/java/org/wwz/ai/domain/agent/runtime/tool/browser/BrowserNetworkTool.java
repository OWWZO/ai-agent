package org.wwz.ai.domain.agent.runtime.tool.browser;

import java.util.LinkedHashMap;
import java.util.Map;

public class BrowserNetworkTool extends AbstractBrowserRelayTool {
    public static final String TOOL_NAME = "browser_network";

    @Override public String getName() { return TOOL_NAME; }
    @Override public String getDescription() { return "OpenCLI network 读取网络请求记录；includeStatic=true 时包含静态资源。"; }
    @Override public Map<String, Object> toParams() {
        return Map.of("type", "object", "properties", Map.of("includeStatic", Map.of("type", "boolean")));
    }
    @Override @SuppressWarnings("unchecked") public Object execute(Object input) {
        Map<String, Object> params = input instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        Map<String, Object> payload = new LinkedHashMap<>();
        if (params.get("includeStatic") instanceof Boolean includeStatic) payload.put("includeStatic", includeStatic);
        return call("network", payload, false);
    }
}
