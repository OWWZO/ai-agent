package org.wwz.ai.domain.agent.runtime.tool.workspace;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.CodeInterpreterResponse;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.FileRequest;
import org.wwz.ai.domain.agent.runtime.dto.FileResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作区文件 → 文件服务登记（不重复上传 content）。
 * 本地已写入 skilloutput/{sessionId}/... 后，只 register 元数据并返回 preview URL。
 */
public final class WorkspaceFileRegistration {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceFileRegistration.class);

    private WorkspaceFileRegistration() {
    }

    /**
     * 本地 workspace 文件登记预览 URL，建立工具产物关联，并同步到会话文件列表。
     * <p>
     * workspace_write/edit 仍会发送普通 {@code tool_result}；这里的 {@code file}
     * 事件仅用于填充前端“全部文件”文件夹，不应自动打开右侧预览。
     * </p>
     *
     * @param relativePath 工作区内相对路径（可含目录）
     * @param absolutePath 本地绝对路径
     * @param commandLabel 前端 file 卡片 command 文案
     */
    public static void registerLocalFile(AgentContext agentContext,
                                         String relativePath,
                                         Path absolutePath,
                                         String commandLabel) {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            return;
        }
        if (absolutePath == null || !Files.isRegularFile(absolutePath)) {
            return;
        }
        try {
            ReactorConfig reactorConfig = agentContext.getRuntimeDependencies().requireReactorConfig();
            FileArtifactPort fileArtifactPort = agentContext.getRuntimeDependencies().requireFileArtifactPort();
            if (reactorConfig == null || StringUtils.isBlank(reactorConfig.getCodeInterpreterUrl())) {
                return;
            }

            String uploadName = normalizeWorkspaceRelativePath(relativePath, absolutePath);
            String baseName = Path.of(uploadName).getFileName().toString();
            if (StringUtils.isBlank(baseName)) {
                baseName = "workspace-file.md";
            }
            if (!baseName.contains(".")) {
                baseName = baseName + ".md";
            }

            long size = Files.size(absolutePath);
            FileRequest fileRequest = FileRequest.builder()
                    .requestId(agentContext.getSessionId())
                    .fileName(uploadName)
                    .description("workspace:" + uploadName)
                    .localPath(absolutePath.toAbsolutePath().normalize().toString())
                    .build();
            FileResponse fileResponse = fileArtifactPort.register(
                    reactorConfig.getCodeInterpreterUrl(), fileRequest);
            if (fileResponse == null) {
                return;
            }
            if (fileResponse.getFileSize() == null) {
                fileResponse.setFileSize((int) Math.min(size, Integer.MAX_VALUE));
            }

            ToolArtifactSource artifactSource = agentContext.getCurrentToolArtifactSource();
            File file = File.builder()
                    .fileName(baseName)
                    .originFileName(uploadName)
                    .relativePath(uploadName)
                    .description("workspace:" + uploadName)
                    .ossUrl(fileResponse.getOssUrl())
                    .domainUrl(fileResponse.getDomainUrl())
                    .fileSize(fileResponse.getFileSize())
                    .isInternalFile(false)
                    .build();
            if (artifactSource != null) {
                agentContext.registerGeneratedArtifact(artifactSource, file);
            }

            if (agentContext.getPrinter() != null) {
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("command", StringUtils.defaultIfBlank(commandLabel, "写入文件"));
                resultMap.put("requestId", agentContext.getSessionId());
                resultMap.put("sessionId", agentContext.getSessionId());
                resultMap.put("relativePath", uploadName);
                resultMap.put("fileListOnly", true);
                if (artifactSource != null) {
                    resultMap.put("toolCallId", artifactSource.getToolCallId());
                    resultMap.put("toolName", artifactSource.getToolName());
                }
                List<CodeInterpreterResponse.FileInfo> fileInfo = new ArrayList<>();
                fileInfo.add(CodeInterpreterResponse.FileInfo.builder()
                        .fileName(baseName)
                        .relativePath(uploadName)
                        .ossUrl(fileResponse.getOssUrl())
                        .domainUrl(fileResponse.getDomainUrl())
                        .fileSize(fileResponse.getFileSize())
                        .build());
                resultMap.put("fileInfo", fileInfo);
                agentContext.getPrinter().send("file", resultMap, null);
            }

            if (log.isDebugEnabled()) {
                log.debug("workspace file registered, command={}, path={}, domainUrl={}",
                        commandLabel, absolutePath, fileResponse.getDomainUrl());
            }
        } catch (Exception e) {
            log.warn("workspace file register failed, path={}", absolutePath, e);
        }
    }

    static String normalizeWorkspaceRelativePath(String relativePath, Path absolutePath) {
        String uploadName = StringUtils.isBlank(relativePath)
                ? (absolutePath == null || absolutePath.getFileName() == null
                ? "workspace-file"
                : absolutePath.getFileName().toString())
                : relativePath.replace('\\', '/').trim();
        while (uploadName.startsWith("./")) {
            uploadName = uploadName.substring(2);
        }
        while (uploadName.startsWith("/")) {
            uploadName = uploadName.substring(1);
        }
        if (StringUtils.isBlank(uploadName)) {
            return fallbackFileName(absolutePath);
        }
        for (String part : uploadName.split("/")) {
            if ("..".equals(part)) {
                return fallbackFileName(absolutePath);
            }
        }
        return uploadName;
    }

    private static String fallbackFileName(Path absolutePath) {
        return absolutePath == null || absolutePath.getFileName() == null
                ? "workspace-file"
                : absolutePath.getFileName().toString();
    }
}
