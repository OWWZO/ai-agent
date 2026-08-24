package org.wwz.ai.domain.agent.runtime.tool.common.social;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpResponse;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 登录态平台工具的远程适配基类。
 *
 * Cookie 只由 reactor-tool 进程读取；Java 工具参数中不允许出现凭据字段。
 */
@Slf4j
@Data
public abstract class AbstractSocialTool implements BaseTool {

    private static final long READ_TIMEOUT_SECONDS = 150L;
    private static final long CALL_TIMEOUT_SECONDS = 180L;

    protected AgentContext agentContext;

    protected abstract String endpointPath();

    protected abstract String platformLabel();

    protected ToolResultPayload executeRemote(Object input) {
        try {
            Map<String, Object> params = coerceMap(input);
            ReactorConfig config = requireReactorConfig();
            String baseUrl = StringUtils.trimToEmpty(config.getCodeInterpreterUrl());
            if (StringUtils.isBlank(baseUrl)) {
                return failure(platformLabel() + " 执行失败：未配置 autobots.autoagent.code_interpreter_url");
            }

            Map<String, Object> body = new LinkedHashMap<>(params);
            body.put("requestId", StringUtils.firstNonBlank(
                    agentContext.getSessionId(), agentContext.getRequestId()));
            String url = joinUrl(baseUrl, endpointPath());
            RemoteHttpResponse response = requireRemoteHttpPort().executeDetailed(RemoteHttpRequest.builder()
                    .method("POST")
                    .url(url)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(JSON.toJSONString(body))
                    .connectTimeoutSeconds(30L)
                    .readTimeoutSeconds(READ_TIMEOUT_SECONDS)
                    .writeTimeoutSeconds(60L)
                    .callTimeoutSeconds(CALL_TIMEOUT_SECONDS)
                    .build());

            JSONObject json = JSON.parseObject(StringUtils.defaultString(response.getBody(), "{}"));
            if (json == null) {
                return failure(platformLabel() + " 执行失败：远程响应为空");
            }

            Object payload = json.get("data");
            JSONObject data = payload instanceof JSONObject ? (JSONObject) payload : json;
            if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
                return ToolResultPayload.failureFrom(
                        platformLabel() + " 执行失败：HTTP " + response.getStatusCode(), data);
            }
            if (data.containsKey("ok") && !data.getBooleanValue("ok")) {
                JSONObject error = data.getJSONObject("error");
                String message = error == null
                        ? platformLabel() + " 返回失败"
                        : StringUtils.defaultIfBlank(error.getString("message"), platformLabel() + " 返回失败");
                return ToolResultPayload.failureFrom(message, data);
            }
            return ToolResultPayload.fromData(data);
        } catch (Exception e) {
            log.error("{} {} remote execution failed", requestId(), getName(), e);
            return failure(platformLabel() + " 执行失败：" + StringUtils.defaultIfBlank(
                    e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    protected Map<String, Object> coerceMap(Object input) {
        if (input instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        if (input instanceof String text && StringUtils.isNotBlank(text)) {
            JSONObject json = JSON.parseObject(text);
            return json == null ? new LinkedHashMap<>() : new LinkedHashMap<>(json);
        }
        return new LinkedHashMap<>();
    }

    protected static Map<String, Object> objectSchema(
            Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties == null ? new LinkedHashMap<>() : properties);
        schema.put("required", required == null ? List.of() : required);
        return schema;
    }

    protected static Map<String, Object> stringProp(String description) {
        return Map.of("type", "string", "description", description);
    }

    protected static Map<String, Object> integerProp(String description, int minimum, int maximum) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "integer");
        property.put("description", description);
        property.put("minimum", minimum);
        property.put("maximum", maximum);
        return property;
    }

    protected static Map<String, Object> enumProp(String description, List<String> values) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "string");
        property.put("description", description);
        property.put("enum", values);
        return property;
    }

    protected ToolResultPayload failure(String message) {
        return ToolResultPayload.failureFrom(message, null);
    }

    protected RemoteHttpPort requireRemoteHttpPort() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException(getName() + " 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireRemoteHttpPort();
    }

    protected ReactorConfig requireReactorConfig() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException(getName() + " 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireReactorConfig();
    }

    protected String requestId() {
        return agentContext == null ? "-" : StringUtils.defaultIfBlank(
                agentContext.getRequestId(), agentContext.getSessionId());
    }

    private static String joinUrl(String baseUrl, String path) {
        String normalizedBase = StringUtils.removeEnd(baseUrl.trim(), "/");
        return normalizedBase + path;
    }
}
