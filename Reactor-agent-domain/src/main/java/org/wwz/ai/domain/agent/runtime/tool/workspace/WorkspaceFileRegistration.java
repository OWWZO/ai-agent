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
     * @param relativePath 工作区内相对路径（可含目录）
     * @param absolutePath 本地绝对路径
     * @param commandLabel SSE file 事件的 command 文案
     * @return 给 tool 结果附加的说明；失败时返回错误提示
     */
    public static String registerLocalFile(AgentContext agentContext,
                                           String relativePath,
                                           Path absolutePath,
                                           String commandLabel) {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            return null;
        }
        if (absolutePath == null || !Files.isRegularFile(absolutePath)) {
            return "登记失败: 本地文件不存在";
        }
        try {
            ReactorConfig reactorConfig = agentContext.getRuntimeDependencies().requireReactorConfig();
            FileArtifactPort fileArtifactPort = agentContext.getRuntimeDependencies().requireFileArtifactPort();
            if (reactorConfig == null || StringUtils.isBlank(reactorConfig.getCodeInterpreterUrl())) {
                return null;
            }

            String uploadName = relativePath == null ? absolutePath.getFileName().toString()
                    : relativePath.replace('\\', '/');
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
                    .fileName(baseName)
                    .description("workspace:" + uploadName)
                    .localPath(absolutePath.toAbsolutePath().normalize().toString())
                    .build();
            FileResponse fileResponse = fileArtifactPort.register(
                    reactorConfig.getCodeInterpreterUrl(), fileRequest);
            if (fileResponse == null) {
                return "登记失败: empty response";
            }
            if (fileResponse.getFileSize() == null) {
                fileResponse.setFileSize((int) Math.min(size, Integer.MAX_VALUE));
            }

            ToolArtifactSource artifactSource = agentContext.getCurrentToolArtifactSource();
            File file = File.builder()
                    .fileName(baseName)
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
                resultMap.put("command", commandLabel == null ? "写入文件" : commandLabel);
                if (artifactSource != null) {
                    resultMap.put("toolCallId", artifactSource.getToolCallId());
                    resultMap.put("toolName", artifactSource.getToolName());
                }
                List<CodeInterpreterResponse.FileInfo> fileInfo = new ArrayList<>();
                fileInfo.add(CodeInterpreterResponse.FileInfo.builder()
                        .fileName(baseName)
                        .ossUrl(fileResponse.getOssUrl())
                        .domainUrl(fileResponse.getDomainUrl())
                        .fileSize(fileResponse.getFileSize())
                        .build());
                resultMap.put("fileInfo", fileInfo);
                agentContext.getPrinter().send("file", resultMap, null);
            }
            return "已登记预览: " + fileResponse.getDomainUrl();
        } catch (Exception e) {
            log.warn("workspace file register failed, path={}", absolutePath, e);
            return "登记失败: " + e.getMessage();
        }
    }
}
