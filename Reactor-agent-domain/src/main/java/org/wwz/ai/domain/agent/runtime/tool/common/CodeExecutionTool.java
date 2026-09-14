package org.wwz.ai.domain.agent.runtime.tool.common;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.CodeInterpreterResponse;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspacePaths;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 调用侧 Agent 直接执行 Python 源码，复用受控 code_execution Runner。 */
@Slf4j
@Data
public class CodeExecutionTool implements BaseTool {
    private AgentContext agentContext;

    @Override public String getName() { return "code_execution"; }

    @Override public String getDescription() {
        return "直接在受控 Python 沙箱执行源码。用于计算、数据处理、图表及用户可下载文件生成。\n"
                + "沙箱 cwd 为当前会话工作区（与 bash 相同）。用 Path / open / savefig 等相对路径读写即可，"
                + "例如 Path('chinagt-shanghai-fire-report/index.html').read_text(encoding='utf-8')；"
                + "plt.savefig('chart.png')；df.to_excel('结果.xlsx')。\n"
                + "本次新增或修改的工作区文件会自动采集上传，前端可预览/下载。\n"
                + "禁止写死宿主绝对路径。fileNames 仅用于 http(s) URL 或工作区外绝对路径。";
    }

    @Override public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("source", Map.of(
                "type", "string",
                "description", "完整 Python 源码。cwd 为会话工作区；生成文件用相对路径，例如 Path('chart.png')、"
                        + "plt.savefig('chart.png')。禁止硬编码 skilloutput/盘符绝对路径。"
        ));
        Map<String, Object> freeformObject = new LinkedHashMap<>();
        freeformObject.put("type", "object");
        freeformObject.put("properties", new LinkedHashMap<String, Object>());
        freeformObject.put("required", List.of());

        Map<String, Object> inputs = new LinkedHashMap<>(freeformObject);
        inputs.put("description", "注入 Python 全局变量的 JSON 对象");
        properties.put("inputs", inputs);
        properties.put("fileNames", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "description", "可选。仅填 http(s) URL 或工作区外绝对路径，系统会下载到会话工作区。"
                        + "工作区已有文件不要填这里，源码里用相对路径 Path/open 直接读。"
        ));
        Map<String, Object> fileItem = new LinkedHashMap<>(freeformObject);
        fileItem.put("description", "工作区文件条目");
        properties.put("files", Map.of("type", "array", "items", fileItem, "description", "写入工作区的小型文本或 Base64 文件"));
        properties.put("timeoutSeconds", Map.of("type", "integer", "minimum", 1, "maximum", 600));
        properties.put("memoryBytes", Map.of("type", "integer", "description", "可选内存上限（字节）"));
        properties.put("importTier", Map.of("type", "string", "enum", List.of("stdlib", "extended", "unrestricted")));
        properties.put("resetWorkspace", Map.of("type", "boolean"));
        properties.put("workspaceFile", Map.of("type", "string", "description", "工作区内已有 Python 源码文件，提供时优先执行它"));
        return Map.of("type", "object", "properties", properties, "required", List.of("source"));
    }

    @Override @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        try {
            Map<String, Object> params = input instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
            String source = params.get("source") == null ? "" : StringUtils.trimToEmpty(String.valueOf(params.get("source")));
            if (source.isBlank()) return ToolResultPayload.failure("source 不能为空", "source 不能为空", null, "missing source");
            ToolArtifactSource artifactSource = agentContext.requireCurrentToolArtifactSource(getName());
            ReactorConfig config = agentContext.getRuntimeDependencies().requireReactorConfig();
            Map<String, Object> request = new LinkedHashMap<>(params);
            request.put("requestId", agentContext.getSessionId());
            request.put("workspaceRoot", WorkspacePaths.resolveSandboxRoot(
                    agentContext.getWorkspaceRoot(), agentContext.getSessionId()).toString());
            request.put("permissionProfile", "workspace");
            request.put("source", source);
            // 远端执行统一绑定当前会话工作区；返回文件再登记到当前 tool artifact source，形成可回放的产物链。
            String body = agentContext.getRuntimeDependencies().requireRemoteHttpPort().execute(RemoteHttpRequest.builder()
                    .method("POST").url(config.getCodeInterpreterUrl() + "/v1/tool/code_execution")
                    .headers(Map.of("Content-Type", "application/json")).body(JSON.toJSONString(request))
                    .connectTimeoutSeconds(60L).readTimeoutSeconds(660L).writeTimeoutSeconds(60L).callTimeoutSeconds(660L).build());
            JSONObject response = JSON.parseObject(body);
            List<CodeInterpreterResponse.FileInfo> fileInfo = JSON.parseArray(response.getString("fileInfo"), CodeInterpreterResponse.FileInfo.class);
            if (fileInfo == null) fileInfo = List.of();
            for (CodeInterpreterResponse.FileInfo info : fileInfo) {
                // 只登记执行服务明确返回的文件，不扫描整个工作区，避免把历史产物误归入本次调用。
                agentContext.registerGeneratedArtifact(artifactSource, File.builder().fileName(info.getFileName())
                        .ossUrl(info.getOssUrl()).domainUrl(info.getDomainUrl()).fileSize(info.getFileSize())
                        .description(info.getFileName()).isInternalFile(false).build());
            }
            if (!fileInfo.isEmpty() && agentContext.getPrinter() != null) {
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("command", "Python 代码执行产物"); event.put("toolCallId", artifactSource.getToolCallId());
                event.put("toolName", artifactSource.getToolName());
                event.put("requestId", agentContext.getSessionId());
                event.put("sessionId", agentContext.getSessionId());
                event.put("fileInfo", fileInfo);
                agentContext.getPrinter().send("file", event, null);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tool", "code_execution");
            data.put("status", response.getString("status"));
            data.put("stdout", WorkspaceService.redactHostPaths(
                    StringUtils.defaultString(response.getString("stdout"))));
            data.put("stderr", WorkspaceService.redactHostPaths(
                    StringUtils.defaultString(response.getString("stderr"))));
            if (response.get("result") != null) {
                Object result = response.get("result");
                data.put("result", result instanceof String s
                        ? WorkspaceService.redactHostPaths(s) : result);
            }
            if (StringUtils.isNotBlank(response.getString("error"))) {
                data.put("error", WorkspaceService.redactHostPaths(response.getString("error")));
            }
            if (!fileInfo.isEmpty()) {
                List<Map<String, Object>> files = new java.util.ArrayList<>();
                for (CodeInterpreterResponse.FileInfo info : fileInfo) {
                    if (info == null) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("file_name", info.getFileName());
                    String url = StringUtils.firstNonBlank(info.getDomainUrl(), info.getOssUrl());
                    if (StringUtils.isNotBlank(url)) {
                        row.put("url", url);
                    }
                    if (info.getFileSize() != null) {
                        row.put("file_size", info.getFileSize());
                    }
                    files.add(row);
                }
                data.put("produced_files", files);
                data.put("hint", "Use produced_files.url as image.url for document_generate when needed.");
            }
            if (!"ok".equals(response.getString("status"))) {
                // stdout/stderr/result 仍保留在失败 payload 中，模型需要这些上下文才能决定是否重试。
                data.put("ok", Boolean.FALSE);
                return ToolResultPayload.failureFrom(
                        StringUtils.defaultIfBlank(response.getString("error"), "code_execution failed"),
                        data
                );
            }
            data.put("ok", Boolean.TRUE);
            return ToolResultPayload.fromData(data);
        } catch (Exception e) {
            log.error("{} code_execution failed", agentContext == null ? "unknown" : agentContext.getRequestId(), e);
            return ToolResultPayload.failureFrom("code_execution 执行失败：" + e.getMessage(), null);
        }
    }
}
