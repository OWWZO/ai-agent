package org.wwz.ai.domain.agent.runtime.tool.browser;

import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BrowserNavigateTool extends AbstractBrowserRelayTool {

    public static final String TOOL_NAME = "browser_navigate";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "在用户已连接的 Chrome/Edge Agent Tab 打开 http(s) URL，随后返回页面标题和无障碍快照。用于已登录站点。公开页请用 WebFetch。";
    }

    @Override
    public Map<String, Object> toParams() {
        return Map.of(
                "type", "object",
                "properties", Map.of("url", Map.of("type", "string", "description", "要打开的 http(s) URL")),
                "required", List.of("url")
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        Map<String, Object> params = input instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        Object url = params.get("url");
        if (url == null || String.valueOf(url).isBlank()) {
            return ToolResultPayload.failure("url 不能为空", "url 不能为空", null, "missing url");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("url", String.valueOf(url).trim());
        return call("navigate", payload, true);
    }
}
