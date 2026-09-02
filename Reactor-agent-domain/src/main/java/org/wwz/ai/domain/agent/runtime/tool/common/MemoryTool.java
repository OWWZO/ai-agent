package org.wwz.ai.domain.agent.runtime.tool.common;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryScope;
import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryStore;
import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryWriteResult;
import org.wwz.ai.domain.agent.memory.ltm.LtmManager;
import org.wwz.ai.domain.agent.memory.ltm.LtmMemoryGuard;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwner;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwnerResolver;
import org.wwz.ai.domain.agent.memory.ltm.LtmPromptGuidance;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Memory tool：add/replace/remove（可选 batch operations）。
 */
@Data
public class MemoryTool implements BaseTool {

    public static final String TOOL_NAME = "memory";

    private AgentContext agentContext;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return LtmPromptGuidance.MEMORY_TOOL_DESCRIPTION;
    }

    @Override
    public Map<String, Object> toParams() {
        // 所有 object 节点显式 properties/required，避免 ToolSchemaNormalizer 告警
        Map<String, Object> actionProp = new LinkedHashMap<>();
        actionProp.put("type", "string");
        actionProp.put("description", "add | replace | remove");
        actionProp.put("enum", List.of("add", "replace", "remove"));

        Map<String, Object> targetProp = new LinkedHashMap<>();
        targetProp.put("type", "string");
        targetProp.put("description", "user (profile) or curated (agent notes)");
        targetProp.put("enum", List.of("user", "curated"));

        Map<String, Object> contentProp = new LinkedHashMap<>();
        contentProp.put("type", "string");
        contentProp.put("description", "Entry content for add/replace");

        Map<String, Object> oldTextProp = new LinkedHashMap<>();
        oldTextProp.put("type", "string");
        oldTextProp.put("description", "Unique substring to locate entry for replace/remove");

        Map<String, Object> opItemProps = new LinkedHashMap<>();
        opItemProps.put("action", actionProp);
        opItemProps.put("content", contentProp);
        opItemProps.put("old_text", oldTextProp);

        Map<String, Object> opItem = new LinkedHashMap<>();
        opItem.put("type", "object");
        opItem.put("description", "One memory mutation");
        opItem.put("properties", opItemProps);
        opItem.put("required", List.of("action"));

        Map<String, Object> operationsProp = new LinkedHashMap<>();
        operationsProp.put("type", "array");
        operationsProp.put("description", "Optional batch ops; each item has action/content/old_text");
        operationsProp.put("items", opItem);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("action", actionProp);
        properties.put("target", targetProp);
        properties.put("content", contentProp);
        properties.put("old_text", oldTextProp);
        properties.put("operations", operationsProp);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("action", "target"));
        return parameters;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        if (LtmMemoryGuard.isSkipMemory(agentContext)) {
            return resultPayload(false, false, LtmMemoryGuard.deniedMessage(), 0, 0);
        }
        CuratedMemoryStore store = resolveStore();
        if (store == null) {
            return resultPayload(false, false, "curated memory store unavailable", 0, 0);
        }
        LtmOwner owner = agentContext != null && agentContext.getLtmOwner() != null
                ? agentContext.getLtmOwner()
                : LtmOwnerResolver.resolve(null, null);
        String sessionId = agentContext == null ? null : agentContext.getSessionId();
        String requestId = agentContext == null ? null : agentContext.getRequestId();

        Map<String, Object> args = normalizeInput(input);
        Object ops = args.get("operations");
        if (ops instanceof List<?> list && !list.isEmpty()) {
            JSONArray results = new JSONArray();
            boolean allOk = true;
            for (Object item : list) {
                Map<String, Object> op = item instanceof Map<?, ?> m
                        ? (Map<String, Object>) m
                        : JSON.parseObject(JSON.toJSONString(item), Map.class);
                CuratedMemoryWriteResult one = applyOne(store, owner, op, sessionId, requestId);
                results.add(toJson(one));
                if (!one.isSuccess()) {
                    allOk = false;
                }
            }
            Map<String, Object> batch = new LinkedHashMap<>();
            batch.put("tool", TOOL_NAME);
            batch.put("ok", allOk);
            batch.put("success", allOk);
            batch.put("results", results);
            return ToolResultPayload.fromData(batch);
        }
        CuratedMemoryWriteResult result = applyOne(store, owner, args, sessionId, requestId);
        JSONObject json = toJson(result);
        json.put("store", store.getClass().getSimpleName());
        json.put("owner_type", owner.getType().name());
        json.put("owner_id", owner.getId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.putAll(json);
        data.put("tool", TOOL_NAME);
        data.put("ok", result.isSuccess());
        return ToolResultPayload.fromData(data);
    }

    private CuratedMemoryWriteResult applyOne(CuratedMemoryStore store,
                                              LtmOwner owner,
                                              Map<String, Object> args,
                                              String sessionId,
                                              String requestId) {
        String action = str(args.get("action")).toLowerCase();
        CuratedMemoryScope scope = CuratedMemoryScope.fromCode(str(args.get("target")));
        String content = str(args.get("content"));
        String oldText = str(args.get("old_text"));
        CuratedMemoryWriteResult result;
        switch (action) {
            case "add" -> result = store.add(owner, scope, content, sessionId, requestId, "assistant_tool");
            case "replace" -> result = store.replace(owner, scope, oldText, content, sessionId, requestId, "assistant_tool");
            case "remove" -> result = store.remove(owner, scope, oldText, sessionId, requestId, "assistant_tool");
            default -> result = CuratedMemoryWriteResult.fail("unknown action: " + action, 0, 0);
        }
        if (result.isSuccess() && !result.isStaged() && agentContext != null
                && !LtmMemoryGuard.isSkipMemory(agentContext)
                && !LtmMemoryGuard.isSideEffectsDisabled(agentContext)
                && agentContext.getRuntimeDependencies() != null) {
            LtmManager manager = agentContext.getRuntimeDependencies().getOptionalLtmManager();
            if (manager != null) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("session_id", sessionId);
                meta.put("request_id", requestId);
                meta.put("write_origin", "assistant_tool");
                if (StringUtils.isNotBlank(oldText)) {
                    meta.put("old_text", oldText);
                }
                manager.notifyMemoryToolWrite(action, scope.getCode(), content, meta);
            }
        }
        return result;
    }

    private CuratedMemoryStore resolveStore() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            return null;
        }
        return agentContext.getRuntimeDependencies().getOptionalCuratedMemoryStore();
    }

    private static Map<String, Object> normalizeInput(Object input) {
        if (input instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        if (input instanceof String s && StringUtils.isNotBlank(s)) {
            return JSON.parseObject(s, Map.class);
        }
        return Map.of();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static JSONObject toJson(CuratedMemoryWriteResult result) {
        JSONObject json = new JSONObject(true);
        json.put("success", result.isSuccess());
        json.put("staged", result.isStaged());
        json.put("no_change", result.isNoChange());
        json.put("message", result.getMessage());
        json.put("used_chars", result.getUsedChars());
        json.put("limit_chars", result.getLimitChars());
        return json;
    }

    private static ToolResultPayload resultPayload(boolean success,
                                                   boolean staged,
                                                   String message,
                                                   int used,
                                                   int limit) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.putAll(toJson(CuratedMemoryWriteResult.builder()
                .success(success)
                .staged(staged)
                .message(message)
                .usedChars(used)
                .limitChars(limit)
                .build()));
        data.put("tool", TOOL_NAME);
        data.put("ok", success);
        return ToolResultPayload.fromData(data);
    }
}
