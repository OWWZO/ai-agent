package org.wwz.ai.domain.agent.runtime.tool.browser;

import java.util.Map;

public class BrowserFramesTool extends AbstractBrowserRelayTool {
    public static final String TOOL_NAME = "browser_frames";

    @Override public String getName() { return TOOL_NAME; }
    @Override public String getDescription() { return "OpenCLI frames 列出当前页面的 frame。"; }
    @Override public Map<String, Object> toParams() {
        return Map.of("type", "object", "properties", Map.of());
    }
    @Override public Object execute(Object input) {
        return call("frames", Map.of(), false);
    }
}
