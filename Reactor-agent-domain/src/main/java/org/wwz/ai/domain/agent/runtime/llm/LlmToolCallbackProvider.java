package org.wwz.ai.domain.agent.runtime.llm;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpRegistry;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.RegistryBackedToolCallback;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 统一封装 Reactor Agent 与 Spring AI 之间的工具回调。
 * <p>
 * - 工具按 name 字典序输出，保证 tools 数组顺序稳定（prompt cache）
 * - 同一会话 + 同一工具签名复用回调列表，避免每轮重建导致 schema 字节漂移
 */
@Component
public class LlmToolCallbackProvider {

    private static final int MAX_SESSION_CACHE = 256;

    @Resource
    private McpRegistry mcpRegistry;

    /** sessionId -> (signature -> callbacks) */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, List<ToolCallback>>> sessionCallbackCache =
            new ConcurrentHashMap<>();

    /**
     * 基于当前 ToolCollection 构建 ToolCallback 列表（固定顺序）。
     */
    public List<ToolCallback> buildToolCallbacks(ToolCollection tools) {
        return buildToolCallbacks(tools, null);
    }

    /**
     * 会话级缓存版本：sessionId 非空时按工具签名复用列表。
     */
    public List<ToolCallback> buildToolCallbacks(ToolCollection tools, String sessionId) {
        if (tools == null) {
            return List.of();
        }
        String signature = buildToolSignature(tools);
        if (StringUtils.isNotBlank(sessionId)) {
            ConcurrentHashMap<String, List<ToolCallback>> bySig =
                    sessionCallbackCache.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>());
            List<ToolCallback> cached = bySig.get(signature);
            if (cached != null) {
                return cached;
            }
            List<ToolCallback> built = buildSortedCallbacks(tools);
            // 不可变视图，防止外部修改污染缓存
            List<ToolCallback> frozen = List.copyOf(built);
            bySig.put(signature, frozen);
            trimSessionCache();
            return frozen;
        }
        return buildSortedCallbacks(tools);
    }

    private List<ToolCallback> buildSortedCallbacks(ToolCollection tools) {
        List<ToolCallback> callbacks = new ArrayList<>();

        List<BaseTool> localTools = tools.getToolMap() == null
                ? List.of()
                : tools.getToolMap().values().stream()
                .filter(t -> t != null && StringUtils.isNotBlank(t.getName()))
                .sorted(Comparator.comparing(BaseTool::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        for (BaseTool tool : localTools) {
            callbacks.add(new BaseToolCallbackAdapter(tool));
        }

        List<McpToolInfo> mcpTools = tools.getMcpToolMap() == null
                ? List.of()
                : tools.getMcpToolMap().values().stream()
                .filter(t -> t != null && StringUtils.isNotBlank(t.getName()))
                .sorted(Comparator.comparing(McpToolInfo::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        for (McpToolInfo toolInfo : mcpTools) {
            callbacks.add(new RegistryBackedToolCallback(mcpRegistry, toolInfo));
        }
        return callbacks;
    }

    /**
     * 工具集签名：排序后的 name 列表（本地 + mcp）。
     * 描述/schema 变化会通过 ToolDefinitionCache key 另行隔离；签名用于会话内列表复用。
     */
    public static String buildToolSignature(ToolCollection tools) {
        if (tools == null) {
            return "";
        }
        List<String> names = new ArrayList<>();
        if (tools.getToolMap() != null) {
            names.addAll(tools.getToolMap().keySet());
        }
        if (tools.getMcpToolMap() != null) {
            for (String name : tools.getMcpToolMap().keySet()) {
                names.add("mcp:" + name);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join("|", names);
    }

    private void trimSessionCache() {
        if (sessionCallbackCache.size() <= MAX_SESSION_CACHE) {
            return;
        }
        // 粗暴淘汰：清一半 key（会话级，体量通常不大）
        int remove = sessionCallbackCache.size() / 2;
        for (String key : sessionCallbackCache.keySet()) {
            sessionCallbackCache.remove(key);
            if (--remove <= 0) {
                break;
            }
        }
    }
}
