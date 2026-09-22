package org.wwz.ai.domain.agent.runtime.tool.browser;

import java.util.Map;

public class BrowserBackTool extends AbstractBrowserRelayTool {

    public static final String TOOL_NAME = "browser_back";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "浏览器后退一页，随后返回 snapshot。";
    }

    @Override
    public Map<String, Object> toParams() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public Object execute(Object input) {
        return call("back", Map.of(), false);
    }
}
