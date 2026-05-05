package org.wwz.ai.domain.agent.runtime.tool.mcp.runtime;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.adapter.repository.IAgentRepository;
import org.wwz.ai.domain.agent.model.valobj.AiClientToolMcpVO;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MCP 统一运行时注册中心。
 * 负责配置加载、客户端预热、工具缓存和统一执行入口。
 */
@Slf4j
@Service
public class McpRegistry {

    /**
     * MCP SDK 大量使用 Java record，使用 Jackson 序列化更稳定。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Resource
    private IAgentRepository repository;

    @Resource
    private McpClientRuntimeFactory runtimeFactory;

    /**
     * 运行时缓存：key 为 mcpId。
     */
    private final Map<String, McpClientRuntime> runtimeCache = new ConcurrentHashMap<>();

    /**
     * 工具发现结果缓存：key 为 mcpId。
     */
    private final Map<String, List<McpToolInfo>> toolCache = new ConcurrentHashMap<>();

    /**
     * fix 策略使用的 ToolCallback 缓存：key 为 mcpId。
     */
    private final Map<String, List<ToolCallback>> toolCallbackCache = new ConcurrentHashMap<>();

    /**
     * 客户端与 MCP 绑定关系缓存：key 为 clientId。
     */
    private final Map<String, List<String>> clientMcpIdCache = new ConcurrentHashMap<>();

    /**
     * 全局启用 MCP 的快照。
     */
    private volatile List<String> globalEnabledMcpIds = Collections.emptyList();

    /**
     * 预热全局启用的 MCP。
     */
    public synchronized void preloadAllEnabledMcps() {
        List<AiClientToolMcpVO> enabledMcpList = repository.queryEnabledAiClientToolMcpVOList();
        Map<String, AiClientToolMcpVO> mcpMap = enabledMcpList.stream()
                .filter(Objects::nonNull)
                .filter(item -> StringUtils.isNotBlank(item.getMcpId()))
                .collect(Collectors.toMap(
                        AiClientToolMcpVO::getMcpId,
                        item -> item,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        syncDisabledMcps(mcpMap.keySet());
        preloadMcps(new ArrayList<>(mcpMap.values()));
        globalEnabledMcpIds = List.copyOf(mcpMap.keySet());

        log.info("MCP 全局预热完成，启用数量：{}", globalEnabledMcpIds.size());
    }

    /**
     * 预热指定客户端关联的 MCP 及其绑定关系。
     */
    public synchronized void preloadClientMcps(List<String> clientIds) {
        if (clientIds == null || clientIds.isEmpty()) {
            return;
        }

        Map<String, List<String>> clientMcpIdMap = repository.queryEnabledClientMcpIdMap(clientIds);
        for (String clientId : clientIds) {
            List<String> mcpIds = clientMcpIdMap.getOrDefault(clientId, Collections.emptyList());
            clientMcpIdCache.put(clientId, List.copyOf(mcpIds));
        }

        List<AiClientToolMcpVO> clientMcpList = repository.AiClientToolMcpVOByClientIds(clientIds);
        preloadMcps(clientMcpList);

        log.info("MCP 客户端绑定预热完成，clientIds={}", clientIds);
    }

    /**
     * 获取全局启用 MCP 的工具列表。
     */
    public List<McpToolInfo> listGlobalEnabledTools() {
        ensureGlobalMcpsLoaded();
        return listToolsByMcpIds(globalEnabledMcpIds);
    }

    /**
     * 获取指定 MCP 列表上的工具列表。
     */
    public List<McpToolInfo> listToolsByMcpIds(List<String> mcpIds) {
        if (mcpIds == null || mcpIds.isEmpty()) {
            return Collections.emptyList();
        }

        ensureMcpsLoaded(mcpIds);

        List<McpToolInfo> toolInfos = new ArrayList<>();
        for (String mcpId : new LinkedHashSet<>(mcpIds)) {
            List<McpToolInfo> oneMcpTools = toolCache.get(mcpId);
            if (oneMcpTools != null && !oneMcpTools.isEmpty()) {
                toolInfos.addAll(oneMcpTools);
            }
        }
        return toolInfos;
    }

    /**
     * 根据 mcpId 列表获取可复用的同步客户端。
     */
    public McpSyncClient[] getSyncClientsByMcpIds(List<String> mcpIds) {
        if (mcpIds == null || mcpIds.isEmpty()) {
            return new McpSyncClient[0];
        }

        ensureMcpsLoaded(mcpIds);

        List<McpSyncClient> clients = new ArrayList<>();
        for (String mcpId : new LinkedHashSet<>(mcpIds)) {
            McpClientRuntime runtime = runtimeCache.get(mcpId);
            if (runtime != null && runtime.getSyncClient() != null) {
                clients.add(runtime.getSyncClient());
            }
        }
        return clients.toArray(new McpSyncClient[0]);
    }

    /**
     * 获取 fix 策略可直接复用的 ToolCallback。
     * 这里在运行时缓存里完成工具发现，并统一套上串行锁，避免 stdio 客户端被并发使用。
     */
    public List<ToolCallback> getToolCallbacksByMcpIds(List<String> mcpIds) {
        if (mcpIds == null || mcpIds.isEmpty()) {
            return Collections.emptyList();
        }

        ensureMcpsLoaded(mcpIds);

        List<ToolCallback> callbacks = new ArrayList<>();
        for (String mcpId : new LinkedHashSet<>(mcpIds)) {
            if (StringUtils.isBlank(mcpId)) {
                continue;
            }
            callbacks.addAll(getOrCreateToolCallbacks(mcpId));
        }
        return callbacks;
    }

    /**
     * 统一执行 MCP 工具。
     */
    public String executeTool(String mcpId, String toolName, Object args) {
        if (StringUtils.isBlank(mcpId) || StringUtils.isBlank(toolName)) {
            return "Tool" + StringUtils.defaultIfBlank(toolName, "Unknown") + " Error.";
        }

        ensureMcpsLoaded(Collections.singletonList(mcpId));
        McpClientRuntime runtime = runtimeCache.get(mcpId);
        if (runtime == null) {
            log.error("MCP 客户端不存在，无法执行工具: mcpId={}, toolName={}", mcpId, toolName);
            return buildErrorResult(toolName);
        }

        try {
            McpSchema.CallToolResult result = callToolWithRuntime(runtime, toolName, args);
            return formatToolResult(toolName, runtime.getDescriptor(), result);
        } catch (Exception e) {
            log.error("MCP 工具执行失败: mcpId={}, toolName={}, reason={}",
                    mcpId, toolName, e.getMessage(), e);
            return buildErrorResult(toolName);
        }
    }

    /**
     * 预热并缓存一批 MCP。
     */
    private void preloadMcps(List<AiClientToolMcpVO> mcpList) {
        if (mcpList == null || mcpList.isEmpty()) {
            return;
        }

        for (AiClientToolMcpVO mcpVO : mcpList) {
            if (mcpVO == null || StringUtils.isBlank(mcpVO.getMcpId())) {
                continue;
            }

            try {
                McpServerDescriptor descriptor = buildDescriptor(mcpVO);
                McpClientRuntime runtime = runtimeFactory.createRuntime(descriptor);
                List<McpToolInfo> tools = discoverTools(runtime);

                McpClientRuntime oldRuntime = runtimeCache.put(mcpVO.getMcpId(), runtime);
                toolCache.put(mcpVO.getMcpId(), tools);
                toolCallbackCache.remove(mcpVO.getMcpId());

                closeQuietly(oldRuntime, runtime);
                log.info("MCP 预热成功: mcpId={}, toolCount={}", mcpVO.getMcpId(), tools.size());
            } catch (Exception e) {
                log.error("MCP 预热失败: mcpId={}, reason={}", mcpVO.getMcpId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 基于已初始化客户端发现工具，并缓存为 Reactor 可直接消费的 McpToolInfo。
     */
    private List<McpToolInfo> discoverTools(McpClientRuntime runtime) {
        List<McpToolInfo> toolInfos = new ArrayList<>();
        runtime.getLock().lock();
        try {
            String cursor = null;
            do {
                McpSchema.ListToolsResult listToolsResult = StringUtils.isBlank(cursor)
                        ? runtime.getSyncClient().listTools()
                        : runtime.getSyncClient().listTools(cursor);

                if (listToolsResult == null || listToolsResult.tools() == null || listToolsResult.tools().isEmpty()) {
                    break;
                }

                for (McpSchema.Tool tool : listToolsResult.tools()) {
                    toolInfos.add(toToolInfo(runtime.getDescriptor(), tool));
                }
                cursor = listToolsResult.nextCursor();
            } while (StringUtils.isNotBlank(cursor));
        } finally {
            runtime.getLock().unlock();
        }
        return toolInfos;
    }

    /**
     * 将数据库配置转换为统一的运行时描述对象。
     */
    private McpServerDescriptor buildDescriptor(AiClientToolMcpVO mcpVO) {
        McpServerDescriptor.McpServerDescriptorBuilder builder = McpServerDescriptor.builder()
                .mcpId(mcpVO.getMcpId())
                .serverKey(mcpVO.getMcpId())
                .transportType(mcpVO.getTransportType())
                .requestTimeout(mcpVO.getRequestTimeout());

        if (McpServerDescriptor.TRANSPORT_TYPE_SSE.equals(mcpVO.getTransportType())) {
            AiClientToolMcpVO.TransportConfigSse configSse = mcpVO.getTransportConfigSse();
            String baseUri = configSse != null ? configSse.getBaseUri() : "";
            String endpoint = configSse != null ? configSse.getSseEndpoint() : "";
            String serverUrl = buildServerUrl(baseUri, endpoint);
            return builder
                    .serverUrl(serverUrl)
                    .baseUri(baseUri)
                    .endpoint(endpoint)
                    .build();
        }

        if (McpServerDescriptor.TRANSPORT_TYPE_STDIO.equals(mcpVO.getTransportType())) {
            AiClientToolMcpVO.TransportConfigStdio transportConfigStdio = mcpVO.getTransportConfigStdio();
            AiClientToolMcpVO.TransportConfigStdio.Stdio stdio = null;
            if (transportConfigStdio != null && transportConfigStdio.getStdio() != null) {
                stdio = transportConfigStdio.getStdio().get(mcpVO.getMcpName());
                if (stdio == null && transportConfigStdio.getStdio().size() == 1) {
                    stdio = transportConfigStdio.getStdio().values().iterator().next();
                }
            }

            return builder
                    .serverUrl("stdio://" + mcpVO.getMcpId())
                    .command(stdio != null ? stdio.getCommand() : null)
                    .args(stdio != null && stdio.getArgs() != null ? stdio.getArgs() : Collections.emptyList())
                    .env(stdio != null && stdio.getEnv() != null ? stdio.getEnv() : Collections.emptyMap())
                    .build();
        }

        if (McpServerDescriptor.TRANSPORT_TYPE_STREAMABLE_HTTP.equals(mcpVO.getTransportType())) {
            AiClientToolMcpVO.TransportConfigStreamableHttp streamableHttp = mcpVO.getTransportConfigStreamableHttp();
            String baseUri = streamableHttp != null ? streamableHttp.getBaseUri() : "";
            String endpoint = streamableHttp != null ? streamableHttp.getEndpoint() : "/mcp";
            Map<String, String> headers = streamableHttp != null && streamableHttp.getHeaders() != null
                    ? streamableHttp.getHeaders()
                    : Collections.emptyMap();
            Boolean resumableStreams = streamableHttp != null ? streamableHttp.getResumableStreams() : false;
            Boolean openConnectionOnStartup = streamableHttp != null ? streamableHttp.getOpenConnectionOnStartup() : true;
            String serverUrl = buildServerUrl(baseUri, endpoint);
            return builder
                    .serverUrl(serverUrl)
                    .baseUri(baseUri)
                    .endpoint(endpoint)
                    .headers(headers)
                    .resumableStreams(Boolean.TRUE.equals(resumableStreams))
                    .openConnectionOnStartup(!Boolean.FALSE.equals(openConnectionOnStartup))
                    .build();
        }

        return builder
                .serverUrl(mcpVO.getTransportConfig())
                .build();
    }

    /**
     * 拼接完整服务地址，便于日志排查。
     */
    private String buildServerUrl(String baseUri, String endpoint) {
        String safeBaseUri = StringUtils.defaultString(baseUri);
        String safeEndpoint = StringUtils.defaultString(endpoint);
        if (StringUtils.isBlank(safeBaseUri)) {
            return safeEndpoint;
        }
        if (StringUtils.isBlank(safeEndpoint)) {
            return safeBaseUri;
        }
        if (safeBaseUri.endsWith("/") && safeEndpoint.startsWith("/")) {
            return safeBaseUri.substring(0, safeBaseUri.length() - 1) + safeEndpoint;
        }
        if (!safeBaseUri.endsWith("/") && !safeEndpoint.startsWith("/")) {
            return safeBaseUri + "/" + safeEndpoint;
        }
        return safeBaseUri + safeEndpoint;
    }

    /**
     * 清理已经被禁用的 MCP 缓存。
     */
    private void syncDisabledMcps(Set<String> enabledMcpIds) {
        Set<String> staleMcpIds = new LinkedHashSet<>(runtimeCache.keySet());
        staleMcpIds.removeAll(enabledMcpIds);

        for (String staleMcpId : staleMcpIds) {
            McpClientRuntime staleRuntime = runtimeCache.remove(staleMcpId);
            toolCache.remove(staleMcpId);
            toolCallbackCache.remove(staleMcpId);
            removeClientBinding(staleMcpId);
            closeQuietly(staleRuntime, null);
            log.info("MCP 已从缓存移除: mcpId={}", staleMcpId);
        }
    }

    /**
     * 从客户端绑定缓存中移除失效的 MCP。
     */
    private void removeClientBinding(String mcpId) {
        clientMcpIdCache.replaceAll((clientId, mcpIds) -> mcpIds.stream()
                .filter(id -> !StringUtils.equals(id, mcpId))
                .toList());
    }

    /**
     * 确保全局 MCP 已预热。
     */
    private void ensureGlobalMcpsLoaded() {
        if (globalEnabledMcpIds.isEmpty()) {
            preloadAllEnabledMcps();
        }
    }

    /**
     * 确保指定 MCP 已完成预热。
     */
    private void ensureMcpsLoaded(List<String> mcpIds) {
        if (mcpIds == null || mcpIds.isEmpty()) {
            return;
        }
        List<String> missingIds = mcpIds.stream()
                .filter(StringUtils::isNotBlank)
                .filter(mcpId -> !runtimeCache.containsKey(mcpId) || !toolCache.containsKey(mcpId))
                .distinct()
                .toList();

        if (!missingIds.isEmpty()) {
            preloadAllEnabledMcps();
        }
    }

    /**
     * 将 SDK 工具定义转成内部统一工具描述。
     */
    private McpToolInfo toToolInfo(McpServerDescriptor descriptor, McpSchema.Tool tool) {
        String parameters = tool.inputSchema() == null ? "{}" : writeAsJson(tool.inputSchema());
        return McpToolInfo.builder()
                .mcpId(descriptor.getMcpId())
                .name(tool.name())
                .desc(StringUtils.defaultIfBlank(tool.description(), tool.title()))
                .parameters(parameters)
                .transportType(descriptor.getTransportType())
                .serverKey(descriptor.resolveServerKey())
                .descriptor(descriptor)
                .build();
    }

    /**
     * 按 mcpId 懒加载并缓存 ToolCallback。
     */
    private List<ToolCallback> getOrCreateToolCallbacks(String mcpId) {
        return toolCallbackCache.computeIfAbsent(mcpId, this::buildToolCallbacks);
    }

    /**
     * 基于共享运行时生成一次性 ToolCallback，并在执行期统一复用。
     * 这样可以避免 SyncMcpToolCallbackProvider 在每次请求时重复 listTools。
     */
    private List<ToolCallback> buildToolCallbacks(String mcpId) {
        McpClientRuntime runtime = runtimeCache.get(mcpId);
        List<McpToolInfo> toolInfos = toolCache.get(mcpId);
        if (runtime == null || runtime.getDescriptor() == null || toolInfos == null || toolInfos.isEmpty()) {
            return Collections.emptyList();
        }

        List<ToolCallback> callbacks = new ArrayList<>(toolInfos.size());
        for (McpToolInfo toolInfo : toolInfos) {
            callbacks.add(new RegistryBackedToolCallback(this, toolInfo));
        }
        return List.copyOf(callbacks);
    }

    /**
     * 根据传输协议选择调用策略。
     * SSE和Stream 继续复用共享客户端；STDIO 每次创建临时运行时，用完即关，避免长连接 transport 状态失效。
     */
    private McpSchema.CallToolResult callToolWithRuntime(McpClientRuntime runtime, String toolName, Object args) {
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(toolName, normalizeArguments(args));
        if (isStdioRuntime(runtime)) {
            return callToolWithTransientRuntime(runtime.getDescriptor(), request);
        }
        return callToolWithSharedRuntime(runtime, request);
    }

    /**
     * 共享运行时调用，适用于 SSE 等可稳定复用的连接。
     */
    private McpSchema.CallToolResult callToolWithSharedRuntime(McpClientRuntime runtime, McpSchema.CallToolRequest request) {
        runtime.getLock().lock();
        try {
            return runtime.getSyncClient().callTool(request);
        } finally {
            runtime.getLock().unlock();
        }
    }

    /**
     * STDIO 运行时调用采用短生命周期策略，避免 transport 复用后内部调度器失效。
     */
    private McpSchema.CallToolResult callToolWithTransientRuntime(McpServerDescriptor descriptor, McpSchema.CallToolRequest request) {
        McpClientRuntime transientRuntime = runtimeFactory.createRuntime(copyDescriptor(descriptor));
        try {
            return transientRuntime.getSyncClient().callTool(request);
        } finally {
            closeRuntimeQuietly(transientRuntime);
        }
    }

    /**
     * 判断是否为 stdio 运行时。
     */
    private boolean isStdioRuntime(McpClientRuntime runtime) {
        return runtime != null
                && runtime.getDescriptor() != null
                && StringUtils.equals(runtime.getDescriptor().getTransportType(), McpServerDescriptor.TRANSPORT_TYPE_STDIO);
    }

    /**
     * 复制服务描述，避免临时运行时污染共享配置对象。
     */
    private McpServerDescriptor copyDescriptor(McpServerDescriptor descriptor) {
        return McpServerDescriptor.builder()
                .mcpId(descriptor.getMcpId())
                .serverUrl(descriptor.getServerUrl())
                .transportType(descriptor.getTransportType())
                .serverKey(descriptor.getServerKey())
                .baseUri(descriptor.getBaseUri())
                .endpoint(descriptor.getEndpoint())
                .requestTimeout(descriptor.getRequestTimeout())
                .command(descriptor.getCommand())
                .args(descriptor.getArgs() == null ? Collections.emptyList() : new ArrayList<>(descriptor.getArgs()))
                .env(descriptor.getEnv() == null ? Collections.emptyMap() : new LinkedHashMap<>(descriptor.getEnv()))
                .headers(descriptor.getHeaders() == null ? Collections.emptyMap() : new LinkedHashMap<>(descriptor.getHeaders()))
                .resumableStreams(Boolean.TRUE.equals(descriptor.getResumableStreams()))
                .openConnectionOnStartup(!Boolean.FALSE.equals(descriptor.getOpenConnectionOnStartup()))
                .build();
    }

    /**
     * 规范化工具参数，统一转换成 Map 结构。
     */
    private Map<String, Object> normalizeArguments(Object args) {
        if (args == null) {
            return Collections.emptyMap();
        }
        if (args instanceof Map<?, ?> mapArgs) {
            return JSON.parseObject(JSON.toJSONString(mapArgs), new TypeReference<Map<String, Object>>() {
            });
        }
        if (args instanceof JSONObject jsonObject) {
            return JSON.parseObject(jsonObject.toJSONString(), new TypeReference<Map<String, Object>>() {
            });
        }
        if (args instanceof String str && JSON.isValidObject(str)) {
            return JSON.parseObject(str, new TypeReference<Map<String, Object>>() {
            });
        }
        return JSON.parseObject(JSON.toJSONString(args), new TypeReference<Map<String, Object>>() {
        });
    }

    /**
     * 将 MCP 返回结果转换为兼容现有 Agent 观察链路的字符串。
     */
    private String formatToolResult(String toolName, McpServerDescriptor descriptor, McpSchema.CallToolResult result) {
        if (result == null) {
            log.error("MCP 工具执行返回空结果: mcpId={}, toolName={}", descriptor.getMcpId(), toolName);
            return buildErrorResult(toolName);
        }

        if (Boolean.TRUE.equals(result.isError())) {
            String errorDetail = extractErrorDetail(result);
            log.error("MCP 工具返回错误结果: mcpId={}, toolName={}, result={}",
                    descriptor.getMcpId(), toolName, writeAsJson(result));
            return buildErrorResult(toolName, errorDetail);
        }

        String textResult = extractTextContent(result.content());
        if (StringUtils.isNotBlank(textResult)) {
            return textResult;
        }
        if (result.structuredContent() != null) {
            return writeAsJson(result.structuredContent());
        }
        if (result.content() != null && !result.content().isEmpty()) {
            return writeAsJson(result.content());
        }
        return writeAsJson(result);
    }

    /**
     * 从 MCP content 中提取文本块。
     */
    private String extractTextContent(List<McpSchema.Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return "";
        }

        StringBuilder textBuilder = new StringBuilder();
        for (McpSchema.Content content : contents) {
            if (content instanceof McpSchema.TextContent textContent && StringUtils.isNotBlank(textContent.text())) {
                if (textBuilder.length() > 0) {
                    textBuilder.append(System.lineSeparator());
                }
                textBuilder.append(textContent.text());
            }
        }
        return textBuilder.toString();
    }

    /**
     * 从错误结果中提取更可读的报错信息。
     */
    private String extractErrorDetail(McpSchema.CallToolResult result) {
        String textContent = extractTextContent(result.content());
        if (StringUtils.isNotBlank(textContent)) {
            return textContent;
        }
        if (result.structuredContent() != null) {
            return writeAsJson(result.structuredContent());
        }
        if (result.content() != null && !result.content().isEmpty()) {
            return writeAsJson(result.content());
        }
        return writeAsJson(result);
    }

    /**
     * 统一生成兼容老链路的错误返回。
     */
    private String buildErrorResult(String toolName) {
        return buildErrorResult(toolName, "");
    }

    /**
     * 生成兼容老链路的错误串，同时保留更明确的报错细节。
     */
    private String buildErrorResult(String toolName, String errorDetail) {
        if (StringUtils.isBlank(errorDetail)) {
            return "Tool" + toolName + " Error.";
        }
        return "Tool" + toolName + " Error. " + errorDetail;
    }

    /**
     * 统一的 JSON 序列化兜底。
     */
    private String writeAsJson(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("MCP 对象序列化失败，降级使用 toString: type={}, reason={}",
                    value.getClass().getName(), e.getMessage());
            return String.valueOf(value);
        }
    }

    /**
     * 安静关闭旧运行时，避免刷新时泄漏连接。
     */
    private void closeQuietly(McpClientRuntime oldRuntime, McpClientRuntime newRuntime) {
        if (oldRuntime == null || oldRuntime == newRuntime || oldRuntime.getSyncClient() == null) {
            return;
        }
        closeRuntimeQuietly(oldRuntime);
    }

    /**
     * 安静关闭运行时。
     */
    private void closeRuntimeQuietly(McpClientRuntime runtime) {
        if (runtime == null || runtime.getSyncClient() == null) {
            return;
        }
        try {
            runtime.getSyncClient().closeGracefully();
        } catch (Exception e) {
            log.warn("关闭旧 MCP 客户端失败: mcpId={}, reason={}",
                    runtime.getDescriptor() != null ? runtime.getDescriptor().getMcpId() : "unknown",
                    e.getMessage());
        }
    }
}
