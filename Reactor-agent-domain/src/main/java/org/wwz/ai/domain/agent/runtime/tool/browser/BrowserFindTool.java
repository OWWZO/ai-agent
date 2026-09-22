package org.wwz.ai.domain.agent.runtime.tool.browser;

import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BrowserFindTool extends AbstractBrowserRelayTool {

    public static final String TOOL_NAME = "browser_find";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "OpenCLI find：css=true 按 CSS selector 查找；css=false 或省略时按可见文本语义查找。返回数字 ref 与 nth。";
    }

    @Override
    public Map<String, Object> toParams() {
        return Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string"), "css", Map.of("type", "boolean", "description", "true 按 CSS selector 查找")), "required", List.of("query"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        Map<String, Object> params = input instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        if (params.get("query") == null) {
            return ToolResultPayload.failure("query 不能为空", "query 不能为空", null, "missing query");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", String.valueOf(params.get("query")));
        if (params.get("css") instanceof Boolean css) payload.put("isCss", css);
        return call("find", payload, false);
    }
}
