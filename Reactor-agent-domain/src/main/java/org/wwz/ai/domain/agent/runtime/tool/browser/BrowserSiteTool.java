package org.wwz.ai.domain.agent.runtime.tool.browser;

import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BrowserSiteTool extends AbstractBrowserRelayTool {
    public static final String TOOL_NAME = "browser_site";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "调用扩展内 OpenCLI 站点适配器。site+command 例如 xiaohongshu/search、twitter/search。只读优先；需要用户已连接浏览器。Cookie 不会上传。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("site", Map.of("type", "string"));
        properties.put("command", Map.of("type", "string"));
        properties.put("query", Map.of("type", "string"));
        properties.put("url", Map.of("type", "string"));
        properties.put("limit", Map.of("type", "integer"));
        properties.put("username", Map.of("type", "string"));
        properties.put("kwargs", Map.of("type", "object"));
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("site", "command")
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        Map<String, Object> params = input instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
        Object site = params.get("site");
        Object command = params.get("command");
        if (site == null || String.valueOf(site).isBlank()) {
            return ToolResultPayload.failure("site 不能为空", "site 不能为空", null, "missing site");
        }
        if (command == null || String.valueOf(command).isBlank()) {
            return ToolResultPayload.failure("command 不能为空", "command 不能为空", null, "missing command");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("site", String.valueOf(site).trim());
        payload.put("command", String.valueOf(command).trim());
        for (String key : List.of("query", "url", "limit", "username")) {
            if (params.get(key) != null) payload.put(key, params.get(key));
        }
        if (params.get("kwargs") instanceof Map<?, ?> kwargs) {
            payload.put("kwargs", new LinkedHashMap<>((Map<String, Object>) kwargs));
        }
        return call("site", payload, false);
    }

    @Override
    protected Duration rpcTimeout() {
        return Duration.ofSeconds(60);
    }
}
