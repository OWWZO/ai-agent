package org.wwz.ai.domain.agent.runtime.tool.browser;

import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BrowserTabsTool extends AbstractBrowserRelayTool {

    public static final String TOOL_NAME = "browser_tabs";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "管理 Agent 浏览器标签。op=list|new|close|select|bind。bind 把用户当前已登录 Tab 捐给 Agent。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("op", Map.of("type", "string", "enum", List.of("list", "new", "close", "select", "bind")));
        properties.put("url", Map.of("type", "string"));
        properties.put("index", Map.of("type", "integer"));
        properties.put("page", Map.of("type", "string", "description", "稳定的页面 targetId；优先于 index 用于 select/close"));
        return Map.of("type", "object", "properties", properties, "required", List.of("op"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        Map<String, Object> params = input instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
        Object op = params.get("op");
        if (op == null || String.valueOf(op).isBlank()) {
            return ToolResultPayload.failure("op 不能为空", "op 不能为空", null, "missing op");
        }
        String action = "bind".equals(String.valueOf(op)) ? "bind" : "tabs";
        boolean mutate = !"list".equals(String.valueOf(op));
        return call(action, params, mutate);
    }
}
