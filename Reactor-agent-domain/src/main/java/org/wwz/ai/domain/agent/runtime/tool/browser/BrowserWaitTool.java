package org.wwz.ai.domain.agent.runtime.tool.browser;

import java.util.LinkedHashMap;
import java.util.Map;

public class BrowserWaitTool extends AbstractBrowserRelayTool {

    public static final String TOOL_NAME = "browser_wait";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "OpenCLI wait：text 默认等待 30 秒，selector 默认 10 秒；仅传 ms 时等待 DOM 稳定。最长 30 秒，超时会报错。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("text", Map.of("type", "string"));
        properties.put("ms", Map.of("type", "integer"));
        properties.put("selector", Map.of("type", "string", "description", "要等待匹配的 CSS selector"));
        return Map.of("type", "object", "properties", properties);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        Map<String, Object> params = input instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
        return call("wait", params, false);
    }
}
