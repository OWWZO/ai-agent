package org.wwz.ai.domain.agent.reactor.agent.tool.mcp.runtime;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.reactor.agent.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 工具统一执行器。
 * 对外提供“发现工具”和“执行工具”两个固定入口，屏蔽底层客户端初始化与并发控制细节。
 */
@Slf4j
@Service
public class McpToolExecutor {

    /**
     * MCP SDK 大量使用 Java record，使用 Jackson 序列化更稳定。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Resource
    private ReactorConfig reactorConfig;

    @Resource
    private McpClientRuntimeFactory runtimeFactory;

    /**
     * 运行时缓存：同一个服务只初始化一个同步客户端。
     */
    private final Map<String, McpClientRuntime> runtimeCache = new ConcurrentHashMap<>();

    /**
     * 根据当前配置生成服务描述列表。
     */
    public List<McpServerDescriptor> getConfiguredServerDescriptors() {
        String[] mcpServerUrlArr = reactorConfig.getMcpServerUrlArr();
        if (mcpServerUrlArr == null || mcpServerUrlArr.length == 0) {
            return Collections.emptyList();
        }

        List<McpServerDescriptor> descriptors = new ArrayList<>();
        for (String serverUrl : mcpServerUrlArr) {
            if (StringUtils.isBlank(serverUrl)) {
                continue;
            }
            descriptors.add(McpServerDescriptor.sse(serverUrl.trim()));
        }
        return descriptors;
    }

    /**
     * 发现当前配置下的全部 MCP 工具。
     */
    public List<McpToolInfo> discoverConfiguredTools() {
        return discoverTools(getConfiguredServerDescriptors());
    }

    /**
     * 发现指定服务列表上的全部 MCP 工具。
     */
    public List<McpToolInfo> discoverTools(List<McpServerDescriptor> descriptors) {
        if (descriptors == null || descriptors.isEmpty()) {
            return Collections.emptyList();
        }

        List<McpToolInfo> toolInfos = new ArrayList<>();
        for (McpServerDescriptor descriptor : descriptors) {
            try {
                McpClientRuntime runtime = getOrCreateRuntime(descriptor);
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
            } catch (Exception e) {
                // 发现失败只记录日志，不阻断本地工具装配。
                log.error("MCP 工具发现失败: serverKey={}, serverUrl={}, transportType={}, reason={}",
                        descriptor.resolveServerKey(), descriptor.getServerUrl(), descriptor.getTransportType(), e.getMessage(), e);
            }
        }
        return toolInfos;
    }

    /**
     * 执行单个 MCP 工具，并将结果统一格式化为字符串。
     */
    public String executeTool(McpToolInfo toolInfo, Object args) {
        if (toolInfo == null || StringUtils.isBlank(toolInfo.getName())) {
            return "ToolUnknown Error.";
        }

        McpServerDescriptor descriptor = resolveDescriptor(toolInfo);
        try {
            McpClientRuntime runtime = getOrCreateRuntime(descriptor);
            McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                    toolInfo.getName(),
                    normalizeArguments(args)
            );

            McpSchema.CallToolResult callToolResult;
            runtime.getLock().lock();
            try {
                callToolResult = runtime.getSyncClient().callTool(request);
            } finally {
                runtime.getLock().unlock();
            }

            return formatToolResult(toolInfo.getName(), runtime.getDescriptor(), callToolResult);
        } catch (Exception e) {
            log.error("MCP 工具执行失败: serverKey={}, serverUrl={}, toolName={}, reason={}",
                    descriptor.resolveServerKey(), descriptor.getServerUrl(), toolInfo.getName(), e.getMessage(), e);
            return buildErrorResult(toolInfo.getName());
        }
    }

    /**
     * 获取或创建指定服务的客户端运行时。
     */
    private McpClientRuntime getOrCreateRuntime(McpServerDescriptor descriptor) {
        String serverKey = descriptor.resolveServerKey();
        return runtimeCache.computeIfAbsent(serverKey, key -> runtimeFactory.createRuntime(descriptor));
    }

    /**
     * 将 SDK 工具定义转成系统内部的 MCP 工具元信息。
     */
    private McpToolInfo toToolInfo(McpServerDescriptor descriptor, McpSchema.Tool tool) {
        String parameters = tool.inputSchema() == null ? "{}" : writeAsJson(tool.inputSchema());
        return McpToolInfo.builder()
                .name(tool.name())
                .desc(StringUtils.defaultIfBlank(tool.description(), tool.title()))
                .parameters(parameters)
                .transportType(descriptor.getTransportType())
                .serverKey(descriptor.resolveServerKey())
                .descriptor(descriptor)
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
            log.error("MCP 工具执行返回空结果: serverKey={}, serverUrl={}, toolName={}",
                    descriptor.resolveServerKey(), descriptor.getServerUrl(), toolName);
            return buildErrorResult(toolName);
        }

        if (Boolean.TRUE.equals(result.isError())) {
            String errorDetail = extractErrorDetail(result);
            log.error("MCP 工具返回错误结果: serverKey={}, serverUrl={}, toolName={}, result={}",
                    descriptor.resolveServerKey(), descriptor.getServerUrl(), toolName, writeAsJson(result));
            return buildErrorResult(toolName, errorDetail);
        }

        // 优先抽取文本内容，保持与原观察链路最兼容。
        String textResult = extractTextContent(result.content());
        if (StringUtils.isNotBlank(textResult)) {
            return textResult;
        }

        // 若不是纯文本内容，则回退为 JSON 字符串。
        if (result.structuredContent() != null) {
            return JSON.toJSONString(result.structuredContent());
        }
        if (result.content() != null && !result.content().isEmpty()) {
            return JSON.toJSONString(result.content());
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
            if (content instanceof McpSchema.TextContent textContent) {
                if (StringUtils.isNotBlank(textContent.text())) {
                    if (textBuilder.length() > 0) {
                        textBuilder.append(System.lineSeparator());
                    }
                    textBuilder.append(textContent.text());
                }
            }
        }
        return textBuilder.toString();
    }

    /**
     * 从错误结果中提取尽可能明确的报错信息，便于模型自我修复参数。
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
     * 解析工具上的服务描述，兼容运行时字段未参与序列化的情况。
     */
    private McpServerDescriptor resolveDescriptor(McpToolInfo toolInfo) {
        if (toolInfo.getDescriptor() != null) {
            return toolInfo.getDescriptor();
        }
        return McpServerDescriptor.builder()
                .serverUrl(toolInfo.getServerKey())
                .serverKey(toolInfo.getServerKey())
                .transportType(StringUtils.defaultIfBlank(toolInfo.getTransportType(), McpServerDescriptor.TRANSPORT_TYPE_SSE))
                .build();
    }

    /**
     * 统一生成兼容老链路的错误返回。
     */
    private String buildErrorResult(String toolName) {
        return buildErrorResult(toolName, "");
    }

    /**
     * 生成兼容老链路的错误串，同时尽量补充可读的 MCP 错误细节。
     */
    private String buildErrorResult(String toolName, String errorDetail) {
        if (StringUtils.isBlank(errorDetail)) {
            return "Tool" + toolName + " Error.";
        }
        return "Tool" + toolName + " Error. " + errorDetail;
    }

    /**
     * 统一的 JSON 序列化兜底，优先保证 record 等 SDK 对象输出完整。
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
}
