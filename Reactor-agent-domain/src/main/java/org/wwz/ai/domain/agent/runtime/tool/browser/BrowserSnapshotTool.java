package org.wwz.ai.domain.agent.runtime.tool.browser;

import java.util.Map;

public class BrowserSnapshotTool extends AbstractBrowserRelayTool {

    public static final String TOOL_NAME = "browser_snapshot";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "读取当前 Agent Tab 的 OpenCLI DOM snapshot（[N]<tag>…）。ref 为数字或 eN；click 也支持 CSS selector 和 nth。";
    }

    @Override
    public Map<String, Object> toParams() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public Object execute(Object input) {
        return call("snapshot", Map.of(), false);
    }
}
