package org.wwz.ai.domain.agent.runtime.artifact;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.FileRequest;
import org.wwz.ai.domain.agent.runtime.dto.FileResponse;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内部文件产物上传适配器。
 * <p>
 * 仅供 deep_search、multimodalagent 等运行时工具落盘产物使用，不注册为 Agent 工具。
 * </p>
 */
@Slf4j
public final class FileArtifactUploader {

    private final AgentContext agentContext;

    public FileArtifactUploader(AgentContext agentContext) {
        this.agentContext = agentContext;
    }

    public ToolResultPayload upload(FileRequest request,
                                    boolean internalFile,
                                    ToolArtifactSource artifactSource) {
        try {
            if (request == null) {
                return ToolResultPayload.failureFrom("文件上传请求为空", null);
            }
            ReactorConfig config = requireReactorConfig();
            FileArtifactPort fileArtifactPort = requireFileArtifactPort();

            request.setRequestId(StringUtils.defaultIfBlank(
                    agentContext.getSessionId(), agentContext.getRequestId()));
            String fileName = StringUtil.removeSpecialChars(request.getFileName()).trim();
            if (fileName.isEmpty()) {
                fileName = "autogen_file";
            }
            if (!fileName.contains(".")) {
                fileName = fileName + ".md";
            }
            request.setFileName(fileName);

            FileResponse response = fileArtifactPort.upload(
                    config.getCodeInterpreterUrl(), request);
            if (response == null) {
                return ToolResultPayload.failureFrom("文件上传失败 " + fileName, null);
            }

            if (artifactSource != null) {
                agentContext.registerGeneratedArtifact(artifactSource, File.builder()
                        .fileName(fileName)
                        .fileSize(response.getFileSize())
                        .ossUrl(response.getOssUrl())
                        .domainUrl(response.getDomainUrl())
                        .description(request.getDescription())
                        .isInternalFile(internalFile)
                        .build());
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ok", Boolean.TRUE);
            data.put("fileName", fileName);
            if (response.getFileSize() != null) {
                data.put("fileSize", response.getFileSize());
            }
            return ToolResultPayload.fromData(data);
        } catch (Exception e) {
            log.warn("internal file artifact upload failed, requestId={}",
                    agentContext == null ? "unknown" : agentContext.getRequestId(), e);
            return ToolResultPayload.failureFrom("文件上传失败: " + e.getMessage(), null);
        }
    }

    private ReactorConfig requireReactorConfig() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("FileArtifactUploader 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireReactorConfig();
    }

    private FileArtifactPort requireFileArtifactPort() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("FileArtifactUploader 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireFileArtifactPort();
    }
}
