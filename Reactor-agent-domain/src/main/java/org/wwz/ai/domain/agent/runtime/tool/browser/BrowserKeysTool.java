package org.wwz.ai.domain.agent.runtime.tool.browser;

import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BrowserKeysTool extends AbstractBrowserRelayTool {

    public static final String TOOL_NAME = "browser_keys";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "向当前页面发送按键或组合键，如 Enter、Escape、Tab、Backspace、Control+a、Meta+c。";
    }

    @Override
    public Map<String, Object> toParams() {
        return Map.of("type", "object", "properties", Map.of("key", Map.of("type", "string")), "required", List.of("key"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        Map<String, Object> params = input instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        if (params.get("key") == null) {
            return ToolResultPayload.failure("key 不能为空", "key 不能为空", null, "missing key");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("key", String.valueOf(params.get("key")));
        return call("keys", payload, false);
    }
}
