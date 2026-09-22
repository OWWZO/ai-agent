package org.wwz.ai.domain.agent.runtime.tool.browser;

import java.util.LinkedHashMap;
import java.util.Map;

public class BrowserScrollTool extends AbstractBrowserRelayTool {

    public static final String TOOL_NAME = "browser_scroll";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "滚动页面。传 ref 时使用 OpenCLI resolver 将该元素滚入视口；否则通过 window.scrollBy 滚动，direction=down|up 与 amount 控制方向和距离。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("ref", Map.of("type", "string"));
        properties.put("direction", Map.of("type", "string", "enum", java.util.List.of("down", "up")));
        properties.put("amount", Map.of("type", "integer"));
        return Map.of("type", "object", "properties", properties);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        Map<String, Object> params = input instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
        return call("scroll", params, false);
    }
}
