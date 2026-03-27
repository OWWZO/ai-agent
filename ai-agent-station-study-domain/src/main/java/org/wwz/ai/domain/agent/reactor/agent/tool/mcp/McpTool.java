package org.wwz.ai.domain.agent.reactor.agent.tool.mcp;


import com.alibaba.fastjson2.JSON;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.agent.tool.BaseTool;
import org.wwz.ai.domain.agent.reactor.agent.util.OkHttpUtil;
import org.wwz.ai.domain.agent.reactor.agent.util.SpringContextHolder;
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
            ReactorConfig reactorConfig = SpringContextHolder.getApplicationContext().getBean(ReactorConfig.class);
            String mcpClientUrl = reactorConfig.getMcpClientUrl() + "/v1/tool/list";
            McpToolRequest mcpToolRequest = McpToolRequest.builder()
                    .server_url(mcpServerUrl)
                    .build();
            String response = OkHttpUtil.postJson(mcpClientUrl, JSON.toJSONString(mcpToolRequest), null, 30L);
            log.info("list tool request: {} response: {}", JSON.toJSONString(mcpToolRequest), response);
            return response;
        } catch (Exception e) {
            log.error("{} list tool error", agentContext.getRequestId(), e);
        }
        return "";
    }

    public String callTool(String mcpServerUrl, String toolName, Object input) {
        try {
            ReactorConfig reactorConfig = SpringContextHolder.getApplicationContext().getBean(ReactorConfig.class);
            String mcpClientUrl = reactorConfig.getMcpClientUrl() + "/v1/tool/call";
            Map<String, Object> params = (Map<String, Object>) input;
            McpToolRequest mcpToolRequest = McpToolRequest.builder()
                    .name(toolName)
                    .server_url(mcpServerUrl)
                    .arguments(params)
                    .build();
            String response = OkHttpUtil.postJson(mcpClientUrl, JSON.toJSONString(mcpToolRequest), null, 30L);
            log.info("call tool request: {} response: {}", JSON.toJSONString(mcpToolRequest), response);
            return response;
        } catch (Exception e) {
            log.error("{} call tool error ", agentContext.getRequestId(), e);
        }
        return "";
    }
}