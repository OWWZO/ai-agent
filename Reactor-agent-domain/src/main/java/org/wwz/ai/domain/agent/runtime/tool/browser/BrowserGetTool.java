package org.wwz.ai.domain.agent.runtime.tool.browser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BrowserGetTool extends AbstractBrowserRelayTool {
    public static final String TOOL_NAME = "browser_get";

    @Override public String getName() { return TOOL_NAME; }
    @Override public String getDescription() { return "OpenCLI get 读取元素 text/value/attrs 或表单 form；ref 为数字/eN 或 CSS selector，form 忽略 ref。"; }
    @Override public Map<String, Object> toParams() {
        return Map.of("type", "object", "properties", Map.of("ref", Map.of("type", "string"), "op", Map.of("type", "string", "enum", List.of("text", "value", "attrs", "form")), "nth", Map.of("type", "integer")), "required", List.of("op"));
    }
    @Override @SuppressWarnings("unchecked") public Object execute(Object input) {
        Map<String, Object> params = input instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        Map<String, Object> payload = new LinkedHashMap<>();
        if (params.get("ref") != null) payload.put("ref", String.valueOf(params.get("ref")).trim());
        if (params.get("op") != null) payload.put("op", String.valueOf(params.get("op")));
        if (params.get("nth") instanceof Number nth) payload.put("nth", nth.intValue());
        return call("get", payload, false);
    }
}
