package org.wwz.ai.domain.agent.runtime.tool.common.docgen;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LeAgent-aligned document generation tools (HTTP → reactor-tool /v1/tool/*).
 */
@Slf4j
@Data
public abstract class AbstractDocGenTool implements BaseTool {

    private static final long TIMEOUT_SECONDS = 240L;

    protected AgentContext agentContext;

    protected abstract String endpointPath();

    protected abstract String defaultDescription();

    protected abstract Map<String, Object> defaultParams();

    @Override
    public String getDescription() {
        return defaultDescription();
    }

    @Override
    public Map<String, Object> toParams() {
        return defaultParams();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        try {
            // 抽象层统一完成参数翻译、requestId 注入、远端调用和结果映射，具体工具只需
            // 提供 endpointPath/defaultParams；这样所有文档生成器共享同一超时与 artifact 协议。
            Map<String, Object> params = coerceMap(input);
            ReactorConfig config = requireReactorConfig();
            String base = StringUtils.trimToEmpty(config.getCodeInterpreterUrl());
            if (StringUtils.isBlank(base)) {
                return failure(getName() + " 执行失败：未配置 autobots.autoagent.code_interpreter_url");
            }
            String url = base.endsWith("/") ? base.substring(0, base.length() - 1) + endpointPath() : base + endpointPath();

            Map<String, Object> body = new LinkedHashMap<>(params);
            body.put("requestId", StringUtils.defaultIfBlank(agentContext.getSessionId(), agentContext.getRequestId()));

            String responseText = requireRemoteHttpPort().execute(RemoteHttpRequest.builder()
                    .method("POST")
                    .url(url)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(JSON.toJSONString(body))
                    .connectTimeoutSeconds(30L)
                    .readTimeoutSeconds(TIMEOUT_SECONDS)
                    .writeTimeoutSeconds(60L)
                    .callTimeoutSeconds(TIMEOUT_SECONDS + 30L)
                    .build());

            JSONObject json = JSON.parseObject(responseText);
            if (json == null) {
                return failure(getName() + " 执行失败：空响应");
            }
            if (json.containsKey("message") && !json.getBooleanValue("success") && !json.containsKey("fileInfo")) {
                return failure(getName() + " 执行失败：" + json.getString("message"));
            }
            boolean success = !json.containsKey("success") || json.getBooleanValue("success");
            if (!success) {
                return failure(getName() + " 执行失败：" + StringUtils.defaultIfBlank(json.getString("message"), responseText));
            }

            ToolArtifactSource artifactSource = agentContext.requireCurrentToolArtifactSource(getName());
            // 远端返回的 fileInfo 是外部文件事实，先注册到当前 tool invocation，再发送前端
            // 文件事件；LLM observation 只保留短摘要，完整 URL 由 artifact 回放提供。
            List<File> files = registerFiles(json.getJSONArray("fileInfo"), artifactSource);
            emitFileMessage(files, artifactSource);

            // 对齐 LeAgent：成功 data 走 serialize_for_llm（JSON），不在此拼中文 prose。
            return ToolResultPayload.fromData(buildLlmData(json, files));
        } catch (Exception e) {
            log.error("{} {} error, input={}", agentContext == null ? "-" : agentContext.getRequestId(), getName(), input, e);
            return failure(getName() + " 执行失败：" + e.getMessage());
        }
    }

    private List<File> registerFiles(JSONArray fileInfoArr, ToolArtifactSource artifactSource) {
        List<File> files = new ArrayList<>();
        if (fileInfoArr == null || fileInfoArr.isEmpty()) {
            return files;
        }
        for (int i = 0; i < fileInfoArr.size(); i++) {
            JSONObject item = fileInfoArr.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String fileName = StringUtils.defaultIfBlank(item.getString("fileName"), item.getString("file_name"));
            if (StringUtils.isBlank(fileName)) {
                continue;
            }
            // 同时兼容 camelCase/snake_case 是为了吸收 reactor-tool 历史响应，不把兼容逻辑
            // 泄漏到每一个具体生成工具。
            Integer size = null;
            if (item.get("fileSize") != null) {
                size = item.getInteger("fileSize");
            } else if (item.get("file_size") != null) {
                size = item.getInteger("file_size");
            }
            File file = File.builder()
                    .fileName(fileName)
                    .fileSize(size)
                    .ossUrl(StringUtils.defaultIfBlank(item.getString("ossUrl"), item.getString("oss_url")))
                    .domainUrl(StringUtils.defaultIfBlank(item.getString("domainUrl"), item.getString("domain_url")))
                    .description(getName() + " 生成文件")
                    .isInternalFile(false)
                    .build();
            agentContext.registerGeneratedArtifact(artifactSource, file);
            files.add(file);
        }
        return files;
    }

    private void emitFileMessage(List<File> files, ToolArtifactSource artifactSource) {
        if (files == null || files.isEmpty() || agentContext.getPrinter() == null) {
            return;
        }
        // 文件事件只负责实时 UI 展示，不能替代 artifact 登记；无 Printer 的后台执行仍保留
        // 文件引用，避免把展示通道可用性当成生成成功条件。
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("command", "生成文档");
        resultMap.put("fileInfo", files);
        if (artifactSource != null) {
            resultMap.put("toolCallId", artifactSource.getToolCallId());
            resultMap.put("toolName", artifactSource.getToolName());
        }
        String messageId = StringUtil.getUUID();
        String digitalEmployee = agentContext.getToolCollection() == null
                ? null
                : agentContext.getToolCollection().getDigitalEmployee(getName());
        agentContext.getPrinter().send(messageId, "file", resultMap, digitalEmployee, true);
    }

    /**
     * 构造成功 data（LeAgent ToolResult.data 语义）。文件完整 URL 由 artifact 摘要补充，
     * data 内只保留短引用，避免 observation 塞长链。
     */
    private Map<String, Object> buildLlmData(JSONObject json, List<File> files) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tool", getName());
        data.put("ok", Boolean.TRUE);
        if (files != null && !files.isEmpty()) {
            List<Map<String, Object>> produced = new ArrayList<>();
            for (File f : files) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("file_name", f.getFileName());
                if (f.getFileSize() != null) {
                    entry.put("file_size", f.getFileSize());
                }
                produced.add(entry);
            }
            data.put("produced_files", produced);
        }
        putIfPresent(data, json, "stats");
        putIfPresent(data, json, "message");
        putIfPresent(data, json, "warnings");
        putIfPresent(data, json, "lint_warnings");
        for (String key : List.of(
                "templates", "template", "themes", "theme", "payload", "resolved",
                "colors", "usage", "saved", "deleted", "name", "kind",
                "path", "variables", "chart_type", "format"
        )) {
            putIfPresent(data, json, key);
        }
        // rendered 可能很大：保留截断摘要，完整交付物看 produced_files / artifact
        if (StringUtils.isNotBlank(json.getString("rendered"))) {
            String rendered = json.getString("rendered");
            if (rendered.length() > 2000) {
                rendered = rendered.substring(0, 2000) + "...[truncated]";
            }
            data.put("rendered", rendered);
        }
        return data;
    }

    private static void putIfPresent(Map<String, Object> data, JSONObject json, String key) {
        if (json == null || !json.containsKey(key) || json.get(key) == null) {
            return;
        }
        Object value = json.get(key);
        if (value instanceof String text && text.length() > 3000) {
            data.put(key, text.substring(0, 3000) + "...[truncated]");
            return;
        }
        data.put(key, value);
    }

    protected ToolResultPayload failure(String message) {
        return ToolResultPayload.failureFrom(message, null);
    }

    protected ReactorConfig requireReactorConfig() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException(getName() + " 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireReactorConfig();
    }

    protected RemoteHttpPort requireRemoteHttpPort() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException(getName() + " 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireRemoteHttpPort();
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> coerceMap(Object input) {
        if (input instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null) {
                    out.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            return out;
        }
        if (input instanceof String s && StringUtils.isNotBlank(s)) {
            JSONObject obj = JSON.parseObject(s);
            if (obj != null) {
                return new LinkedHashMap<>(obj);
            }
        }
        return new LinkedHashMap<>();
    }

    protected static Map<String, Object> stringProp(String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "string");
        m.put("description", description);
        return m;
    }

    protected static Map<String, Object> boolProp(String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "boolean");
        m.put("description", description);
        return m;
    }

    protected static Map<String, Object> arrayProp(String description, Map<String, Object> items) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "array");
        m.put("description", description);
        m.put("items", items);
        return m;
    }

    protected static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties != null ? properties : new LinkedHashMap<String, Object>());
        parameters.put("required", required != null ? required : List.of());
        return parameters;
    }

    /** 自由形态 object：显式 properties/required，避免 ToolSchemaNormalizer 告警。 */
    protected static Map<String, Object> objectProp(String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        if (description != null && !description.isBlank()) {
            m.put("description", description);
        }
        m.put("properties", new LinkedHashMap<String, Object>());
        m.put("required", List.of());
        return m;
    }

    protected static String formatList(String... items) {
        return String.join(", ", items);
    }
}
