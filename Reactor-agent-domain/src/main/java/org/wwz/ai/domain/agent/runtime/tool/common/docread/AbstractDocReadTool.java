package org.wwz.ai.domain.agent.runtime.tool.common.docread;

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
 * 文档读取工具的通用远端适配基类。
 * <p>
 * Java 领域层负责参数规范化、工作区上下文注入、远端调用结果解析和产物登记；具体文件解析由 reactor-tool 完成。
 * 成功结果会压缩为适合 LLM 观察的 data，同时将生成文件登记到本轮 artifact source。
 */
@Slf4j
@Data
public abstract class AbstractDocReadTool implements BaseTool {

    private static final long DEFAULT_TIMEOUT_SECONDS = 180L;
    /** data 字段进 llmData 前的本地上限（最终仍受 BaseAgent maxObserve / 96k 约束）。 */
    private static final int DATA_FIELD_LIMIT = 12_000;

    protected AgentContext agentContext;

    protected abstract String endpointPath();

    protected abstract String defaultDescription();

    protected abstract Map<String, Object> defaultParams();

    /** HTTP read/call timeout; override for long cloud jobs (e.g. OCR). */
    protected long timeoutSeconds() {
        return DEFAULT_TIMEOUT_SECONDS;
    }

    @Override
    public String getDescription() {
        return defaultDescription();
    }

    @Override
    public Map<String, Object> toParams() {
        return defaultParams();
    }

    @Override
    public Object execute(Object input) {
        try {
            // 工具输入可能来自 JSON 字符串或结构化 Map，先统一成字符串键的参数表。
            Map<String, Object> params = coerceMap(input);
            ReactorConfig config = requireReactorConfig();
            String base = StringUtils.trimToEmpty(config.getCodeInterpreterUrl());
            if (StringUtils.isBlank(base)) {
                return failure(getName() + " 执行失败：未配置 autobots.autoagent.code_interpreter_url");
            }
            String url = base.endsWith("/")
                    ? base.substring(0, base.length() - 1) + endpointPath()
                    : base + endpointPath();

            // requestId 和 workspace_root 是领域侧注入的运行时元数据，不依赖模型自行传入。
            Map<String, Object> body = new LinkedHashMap<>(params);
            body.put("requestId", StringUtils.defaultIfBlank(agentContext.getSessionId(), agentContext.getRequestId()));
            if (StringUtils.isNotBlank(agentContext.getWorkspaceRoot()) && !body.containsKey("workspace_root")) {
                body.put("workspace_root", agentContext.getWorkspaceRoot());
            }

            long timeoutSeconds = timeoutSeconds();
            // 文档读取默认带 workspace_root，远端解析器可把相对路径限制在当前 session；超时
            // 由具体 reader 的 timeoutSeconds 决定，调用链统一预留连接/写入/总超时。
            String responseText = requireRemoteHttpPort().execute(RemoteHttpRequest.builder()
                    .method("POST")
                    .url(url)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(JSON.toJSONString(body))
                    .connectTimeoutSeconds(30L)
                    .readTimeoutSeconds(timeoutSeconds)
                    .writeTimeoutSeconds(60L)
                    .callTimeoutSeconds(timeoutSeconds + 30L)
                    .build());

            JSONObject json = JSON.parseObject(responseText);
            if (json == null) {
                return failure(getName() + " 执行失败：空响应");
            }
            boolean success = !json.containsKey("success") || json.getBooleanValue("success");
            if (!success) {
                return failure(getName() + " 执行失败："
                        + StringUtils.defaultIfBlank(json.getString("message"), responseText));
            }

            // fileInfo 先登记到账本关联的 artifact source，再向前端发送文件事件；正文仍由
            // buildLlmData 按上限裁剪，避免大文档直接进入下一轮 prompt。
            ToolArtifactSource artifactSource = agentContext.requireCurrentToolArtifactSource(getName());
            List<File> files = registerFiles(json.getJSONArray("fileInfo"), artifactSource);
            emitFileMessage(files, artifactSource);

            // 对齐工具协议：返回给 LLM 的 data 受长度上限保护，完整文件通过 artifact 引用交付。
            return ToolResultPayload.fromData(buildLlmData(json, files));
        } catch (Exception e) {
            log.error("{} {} error, input={}", agentContext == null ? "-" : agentContext.getRequestId(),
                    getName(), input, e);
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
                    .description(getName() + " 输出文件")
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
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("command", "文档处理");
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
        Object payload = json.get("data");
        if (payload != null) {
            if (payload instanceof String text) {
                if (text.length() > DATA_FIELD_LIMIT) {
                    data.put("data", text.substring(0, DATA_FIELD_LIMIT) + "...[truncated]");
                } else {
                    data.put("data", text);
                }
            } else {
                String encoded = JSON.toJSONString(payload);
                if (encoded.length() > DATA_FIELD_LIMIT) {
                    data.put("data", encoded.substring(0, DATA_FIELD_LIMIT) + "...[truncated]");
                } else {
                    data.put("data", payload);
                }
            }
        }
        if (StringUtils.isNotBlank(json.getString("message"))) {
            data.put("message", json.getString("message"));
        }
        return data;
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

    protected static Map<String, Object> intProp(String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "integer");
        m.put("description", description);
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
}
