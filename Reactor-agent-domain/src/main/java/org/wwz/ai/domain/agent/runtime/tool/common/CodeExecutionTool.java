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
                + "【产物路径硬性规则】\n"
                + "1. 需要生成/保存文件时，必须用 build_output_path('文件名') 得到路径再写入；"
                + "系统只采集并上传该 helper 对应目录中的新文件，前端才能预览/下载。\n"
                + "2. 正确示例：Path(build_output_path('chart.png')).write_bytes(...)；"
                + "plt.savefig(build_output_path('chart.png'))；"
                + "df.to_excel(build_output_path('结果.xlsx'))。\n"
                + "3. 禁止写死绝对路径（如 D:\\\\...\\\\skilloutput\\\\session-...\\\\xxx），"
                + "禁止仅用相对文件名 savefig('a.png') 或随意 Path('a.png') 期望自动注册"
                + "（未走 build_output_path 的路径可能不上传、不展示）。\n"
                + "4. 读会话输入文件用 resolve_input_path('文件名')；"
                + "沙箱已注入 build_output_path / resolve_input_path / read_text_file / write_text_file，无需 import。";
    }

    @Override public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("source", Map.of(
                "type", "string",
                "description", "完整 Python 源码。生成文件时必须 Path(build_output_path('name.ext')) 或 "
                        + "plt.savefig(build_output_path('name.ext')) 等；禁止硬编码 skilloutput/盘符绝对路径。"
        ));
        properties.put("inputs", Map.of("type", "object", "description", "注入 Python 全局变量的 JSON 对象"));
        properties.put("fileNames", Map.of("type", "array", "items", Map.of("type", "string"), "description", "会话输入文件名"));
        properties.put("files", Map.of("type", "array", "items", Map.of("type", "object"), "description", "写入工作区的小型文本或 Base64 文件"));
        properties.put("timeoutSeconds", Map.of("type", "integer", "minimum", 1, "maximum", 600));
        properties.put("memoryBytes", Map.of("type", "integer", "description", "可选内存上限（字节）"));
        properties.put("importTier", Map.of("type", "string", "enum", List.of("stdlib", "extended", "unrestricted")));
        properties.put("permissionProfile", Map.of("type", "string", "enum", List.of("analysis", "workspace")));
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
            // 与 workspace_write 同一会话目录：reactor-tool/skilloutput/{sessionId}
            request.put("workspaceRoot", WorkspacePaths.skillOutputSessionRoot(agentContext.getSessionId()).toString());
            request.put("source", source);
            String body = agentContext.getRuntimeDependencies().requireRemoteHttpPort().execute(RemoteHttpRequest.builder()
                    .method("POST").url(config.getCodeInterpreterUrl() + "/v1/tool/code_execution")
                    .headers(Map.of("Content-Type", "application/json")).body(JSON.toJSONString(request))
                    .connectTimeoutSeconds(60L).readTimeoutSeconds(660L).writeTimeoutSeconds(60L).callTimeoutSeconds(660L).build());
            JSONObject response = JSON.parseObject(body);
            List<CodeInterpreterResponse.FileInfo> fileInfo = JSON.parseArray(response.getString("fileInfo"), CodeInterpreterResponse.FileInfo.class);
            if (fileInfo == null) fileInfo = List.of();
            for (CodeInterpreterResponse.FileInfo info : fileInfo) {
                agentContext.registerGeneratedArtifact(artifactSource, File.builder().fileName(info.getFileName())
                        .ossUrl(info.getOssUrl()).domainUrl(info.getDomainUrl()).fileSize(info.getFileSize())
                        .description(info.getFileName()).isInternalFile(false).build());
            }
            if (!fileInfo.isEmpty() && agentContext.getPrinter() != null) {
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("command", "Python 代码执行产物"); event.put("toolCallId", artifactSource.getToolCallId());
                event.put("toolName", artifactSource.getToolName()); event.put("fileInfo", fileInfo);
                agentContext.getPrinter().send("file", event, null);
            }
            String observation = "status=" + response.getString("status") + "\nstdout:\n"
                    + StringUtils.defaultString(response.getString("stdout")) + "\nstderr:\n"
                    + StringUtils.defaultString(response.getString("stderr")) + "\nresult:\n"
                    + JSON.toJSONString(response.get("result"))
                    + formatArtifactUrls(fileInfo);
            if (!"ok".equals(response.getString("status"))) {
                return ToolResultPayload.failure(observation, observation, null, response.getString("error"));
            }
            return ToolResultPayload.text(observation);
        } catch (Exception e) {
            log.error("{} code_execution failed", agentContext == null ? "unknown" : agentContext.getRequestId(), e);
            return ToolResultPayload.failure("code_execution 执行失败：" + e.getMessage(), "code_execution 执行失败", null, e.getMessage());
        }
    }

    /**
     * 上传发生在 Python 执行结束后，只有这里能把最终文件 URL 回传给主智能体。
     */
    private String formatArtifactUrls(List<CodeInterpreterResponse.FileInfo> files) {
        if (files == null || files.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder("\n后续工具可用产物（图片传给 document_generate 时使用 image.url）：");
        for (CodeInterpreterResponse.FileInfo file : files) {
            if (file == null) {
                continue;
            }
            String url = StringUtils.firstNonBlank(file.getDomainUrl(), file.getOssUrl());
            if (StringUtils.isBlank(url)) {
                continue;
            }
            result.append("\n- fileName:").append(StringUtils.defaultString(file.getFileName()))
                    .append(" url:").append(url);
        }
        return result.toString();
    }
}
