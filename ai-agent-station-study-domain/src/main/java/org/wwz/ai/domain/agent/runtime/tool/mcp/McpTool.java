package org.wwz.ai.domain.agent.runtime.tool.mcp;


import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.util.Map;

@Slf4j
@Data
public class McpTool implements BaseTool {
    private AgentContext agentContext;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpToolRequest {
        private String server_url;
        private String name;
        private Map<String, Object> arguments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpToolResponse {
        private String code;
        private String message;
        private String data;
    }

    @Override
    public String getName() {
        return "mcp_tool";
    }

    @Override
    public String getDescription() {
        return "";
    }

    @Override
    public Map<String, Object> toParams() {
        return null;
    }

    @Override
    public Object execute(Object input) {
        return null;
    }

    public String listTool(String mcpServerUrl) {
        try {

            //获取reactor配置
            ReactorConfig reactorConfig = requireReactorConfig();

            //构建通用mcp客户端请求路径
            String mcpClientUrl = reactorConfig.getMcpClientUrl() + "/v1/tool/list";

            //构建请求参数
            McpToolRequest mcpToolRequest = McpToolRequest.builder()
                    .server_url(mcpServerUrl)
                    .build();

            //发送请求
            String response = agentContext.getRuntimeDependencies().requireRemoteHttpPort().execute(RemoteHttpRequest.builder()
                    .method("POST")
                    .url(mcpClientUrl)
                    .headers(java.util.Map.of("Content-Type", "application/json"))
                    .body(JSON.toJSONString(mcpToolRequest))
                    .connectTimeoutSeconds(30L)
                    .readTimeoutSeconds(30L)
                    .writeTimeoutSeconds(30L)
                    .callTimeoutSeconds(30L)
                    .build());

            log.info("list tool request: {} response: {}", JSON.toJSONString(mcpToolRequest), response);

            return response;
        } catch (Exception e) {
            log.error("{} list tool error", agentContext.getRequestId(), e);
        }
        return "";
    }

    public String callTool(String mcpServerUrl, String toolName, Object input) {
        try {
            ReactorConfig reactorConfig = requireReactorConfig();
            String mcpClientUrl = reactorConfig.getMcpClientUrl() + "/v1/tool/call";
            Map<String, Object> params = (Map<String, Object>) input;
            McpToolRequest mcpToolRequest = McpToolRequest.builder()
                    .name(toolName)
                    .server_url(mcpServerUrl)
                    .arguments(params)
                    .build();
            String response = agentContext.getRuntimeDependencies().requireRemoteHttpPort().execute(RemoteHttpRequest.builder()
                    .method("POST")
                    .url(mcpClientUrl)
                    .headers(java.util.Map.of("Content-Type", "application/json"))
                    .body(JSON.toJSONString(mcpToolRequest))
                    .connectTimeoutSeconds(30L)
                    .readTimeoutSeconds(30L)
                    .writeTimeoutSeconds(30L)
                    .callTimeoutSeconds(30L)
                    .build());
            log.info("call tool request: {} response: {}", JSON.toJSONString(mcpToolRequest), response);
            return response;
        } catch (Exception e) {
            log.error("{} call tool error ", agentContext.getRequestId(), e);
        }
        return "";
    }

    private ReactorConfig requireReactorConfig() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("McpTool 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireReactorConfig();
    }
}
