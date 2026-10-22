package org.wwz.ai.domain.agent.runtime.tool.workspace;

import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.cute.config.CuteConfig;
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
 * <p>
 * 本地文件统一走 {@link FileArtifactPort#materializeLocalFile}：共享卷部署走零拷贝 register，
 * 非共享部署由基础设施适配器改为二进制 upload，工具层不需要感知网络拓扑。
 */
public final class WorkspaceFileRegistration {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceFileRegistration.class);

    private WorkspaceFileRegistration() {
    }

    /** 登记失败原因：运行时或文件服务不可用。 */
    public static final String REASON_UNAVAILABLE = "artifact_port_unavailable";
    /** 登记失败原因：本地文件不存在。 */
    public static final String REASON_FILE_MISSING = "file_not_found";
    /** 登记失败原因：文件服务返回空响应。 */
    public static final String REASON_EMPTY_RESPONSE = "artifact_register_failed";

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
     * @return 结构化登记结果，失败时携带原因
     */
    public static LocalFileRegistrationResult registerLocalFile(AgentContext agentContext,
                                                                String relativePath,
                                                                Path absolutePath,
                                                                String commandLabel) {
        return registerLocalFile(agentContext, relativePath, absolutePath, commandLabel, true);
    }

    /**
     * @param emitFileEvent 是否由本方法单独发送 {@code file} 事件；
     *                      批量物化场景设为 false，由调用方聚合后发一条事件。
     */
    public static LocalFileRegistrationResult registerLocalFile(AgentContext agentContext,
                                                                String relativePath,
                                                                Path absolutePath,
                                                                String commandLabel,
                                                                boolean emitFileEvent) {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            return LocalFileRegistrationResult.failure(REASON_UNAVAILABLE);
        }
        if (absolutePath == null || !Files.isRegularFile(absolutePath)) {
            return LocalFileRegistrationResult.failure(REASON_FILE_MISSING);
        }
        try {
            CuteConfig cuteConfig = agentContext.getRuntimeDependencies().requireCuteConfig();
            FileArtifactPort fileArtifactPort = agentContext.getRuntimeDependencies().requireFileArtifactPort();
            if (cuteConfig == null || StringUtils.isBlank(cuteConfig.getCodeInterpreterUrl())) {
                return LocalFileRegistrationResult.failure(REASON_UNAVAILABLE);
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
            String requestId = StringUtils.defaultIfBlank(
                    agentContext.getSessionId(), agentContext.getRequestId());
            FileRequest fileRequest = FileRequest.builder()
                    .requestId(requestId)
                    .fileName(uploadName)
                    .description("workspace:" + uploadName)
                    .localPath(absolutePath.toAbsolutePath().normalize().toString())
                    .build();
            FileResponse fileResponse = fileArtifactPort.materializeLocalFile(
                    cuteConfig.getCodeInterpreterUrl(), fileRequest);
            if (fileResponse == null) {
                return LocalFileRegistrationResult.failure(REASON_EMPTY_RESPONSE);
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

            if (emitFileEvent && agentContext.getPrinter() != null) {
                agentContext.getPrinter().send("file",
                        buildFileEvent(agentContext, artifactSource, baseName, uploadName, fileResponse, commandLabel),
                        null);
            }

            if (log.isDebugEnabled()) {
                log.debug("workspace file registered, command={}, path={}, domainUrl={}",
                        commandLabel, absolutePath, fileResponse.getDomainUrl());
            }
            return LocalFileRegistrationResult.success(uploadName, size, fileResponse);
        } catch (Exception e) {
            log.warn("workspace file register failed, path={}", absolutePath, e);
            return LocalFileRegistrationResult.failure(REASON_EMPTY_RESPONSE);
        }
    }

    private static Map<String, Object> buildFileEvent(AgentContext agentContext,
                                                      ToolArtifactSource artifactSource,
                                                      String baseName,
                                                      String uploadName,
                                                      FileResponse fileResponse,
                                                      String commandLabel) {
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("command", StringUtils.defaultIfBlank(commandLabel, "写入文件"));
        resultMap.put("requestId", StringUtils.defaultIfBlank(
                agentContext.getSessionId(), agentContext.getRequestId()));
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
        return resultMap;
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

    /**
     * 单个本地文件的登记结果。
     * 调用方据此决定是否把文件放进工具结果，不要把失败项当作 artifact。
     */
    @Value
    @Builder
    public static class LocalFileRegistrationResult {

        boolean success;
        /** 失败原因，取本类外层常量；成功时为空。 */
        String reason;
        /** 归一化后的 workspace 相对路径。 */
        String relativePath;
        long fileSize;
        FileResponse response;

        public static LocalFileRegistrationResult failure(String reason) {
            return LocalFileRegistrationResult.builder()
                    .success(false)
                    .reason(reason)
                    .build();
        }

        public static LocalFileRegistrationResult success(String relativePath, long fileSize, FileResponse response) {
            return LocalFileRegistrationResult.builder()
                    .success(true)
                    .relativePath(relativePath)
                    .fileSize(fileSize)
                    .response(response)
                    .build();
        }
    }
}
