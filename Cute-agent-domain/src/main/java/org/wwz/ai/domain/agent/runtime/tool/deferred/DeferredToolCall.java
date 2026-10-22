package org.wwz.ai.domain.agent.runtime.tool.deferred;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.McpToolNames;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ToolCall 解析 / probe-validate。pipeline 与 ToolCallTool 共用，避免双执行。
 */
public final class DeferredToolCall {

    private DeferredToolCall() {
    }

    public static Result resolve(ToolCollection collection, Object rawArgs) {
        Map<String, Object> args = coerceMap(rawArgs);
        String name = String.valueOf(args.getOrDefault("name", "")).trim();
        if (name.isEmpty() || "null".equals(name)) {
            return Result.error(payload("ToolCall requires a 'name' argument"));
        }
        if (McpToolNames.isBridge(name)) {
            return Result.error(payload("ToolCall cannot invoke '" + name + "' (it is itself a bridge tool)"));
        }
        DeferredToolCatalog catalog = collection == null ? null : collection.getDeferredToolCatalog();
        if (catalog == null || !catalog.contains(name)) {
            return Result.error(payload("'" + name + "' is not a deferred tool in this collection. "
                    + "If it already appears in tools[], call it directly; otherwise ToolSearch / ToolDescribe first."));
        }
        Object parsedArgs;
        try {
            parsedArgs = parseArguments(args.get("arguments"));
        } catch (IllegalArgumentException e) {
            return Result.error(payload(e.getMessage()));
        }
        if (!(parsedArgs instanceof Map<?, ?>)) {
            return Result.error(payload("ToolCall 'arguments' must be an object"));
        }
        Map<String, Object> underlyingArgs = coerceMap(parsedArgs);
        DeferredToolEntry tool = catalog.get(name);
        Object missing = missingRequiredPayload(name, tool, underlyingArgs);
        if (missing != null) {
            return Result.error(missing);
        }
        return Result.ok(name, underlyingArgs);
    }

    public static Map<String, Object> coerceMap(Object input) {
        if (input == null) {
            return new LinkedHashMap<>();
        }
        if (input instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null) {
                    result.put(String.valueOf(k), v);
                }
            });
            return result;
        }
        if (input instanceof String str && StringUtils.isNotBlank(str)) {
            try {
                Object parsed = JSON.parse(str);
                if (parsed instanceof Map<?, ?>) {
                    return coerceMap(parsed);
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return new LinkedHashMap<>();
    }

    public static Object parseParametersSchema(String parameters) {
        if (StringUtils.isBlank(parameters)) {
            return Map.of();
        }
        try {
            Object parsed = JSON.parse(parameters);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static Object parseArguments(Object rawArgs) {
        if (rawArgs == null) {
            return Map.of();
        }
        if (rawArgs instanceof Map<?, ?>) {
            return rawArgs;
        }
        if (rawArgs instanceof String str) {
            if (StringUtils.isBlank(str)) {
                return Map.of();
            }
            try {
                return JSON.parse(str);
            } catch (Exception e) {
                throw new IllegalArgumentException("ToolCall 'arguments' is not valid JSON: " + e.getMessage());
            }
        }
        return rawArgs;
    }

    private static Object missingRequiredPayload(String name, DeferredToolEntry tool, Map<String, Object> args) {
        if (tool == null || tool.getParameters() == null || tool.getParameters().isEmpty()) {
            return null;
        }
        try {
            JSONObject root = JSON.parseObject(JSON.toJSONString(tool.getParameters()));
            if (root == null) {
                return null;
            }
            JSONArray required = root.getJSONArray("required");
            if (required == null || required.isEmpty()) {
                return null;
            }
            List<String> missing = new ArrayList<>();
            for (int i = 0; i < required.size(); i++) {
                Object item = required.get(i);
                if (item instanceof String key && !args.containsKey(key)) {
                    missing.add(key);
                }
            }
            if (missing.isEmpty()) {
                return null;
            }
            String message = "ToolCall to '" + name + "' is missing required argument(s): "
                    + String.join(", ", missing) + ". The tool was NOT invoked.";
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("error", message);
            fields.put("parameters", tool.getParameters());
            fields.put("hint", "Retry ToolCall with 'arguments' matching the parameters schema above.");
            return ToolResultPayload.failureFrom(message, fields);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ToolResultPayload payload(String message) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("error", message);
        return ToolResultPayload.failureFrom(message, fields);
    }

    public static final class Result {
        private final boolean ok;
        private final String name;
        private final Map<String, Object> arguments;
        private final Object errorPayload;

        private Result(boolean ok, String name, Map<String, Object> arguments, Object errorPayload) {
            this.ok = ok;
            this.name = name;
            this.arguments = arguments == null ? Collections.emptyMap() : arguments;
            this.errorPayload = errorPayload;
        }

        static Result ok(String name, Map<String, Object> arguments) {
            return new Result(true, name, arguments, null);
        }

        static Result error(Object payload) {
            return new Result(false, null, Map.of(), payload);
        }

        public boolean ok() {
            return ok;
        }

        public String name() {
            return name;
        }

        public Map<String, Object> arguments() {
            return arguments;
        }

        public Object errorPayload() {
            return errorPayload;
        }
    }
}
