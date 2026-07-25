package org.wwz.ai.domain.agent.runtime.tool.workspace;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.FileRequest;
import org.wwz.ai.domain.agent.runtime.dto.FileResponse;
import org.wwz.ai.domain.agent.runtime.dto.CodeInterpreterResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写入工作区文本文件（对齐 cchaha Write）。
 * 本地写入成功后，尽力同步到文件服务，供 UI 工作区预览。
 */
public class WorkspaceWriteTool extends AbstractWorkspacePathTool {

    public WorkspaceWriteTool(WorkspaceService workspaceService, WorkspaceRuntimeOptions workspaceRuntimeOptions) {
        super(workspaceService, workspaceRuntimeOptions);
    }

    @Override
    public String getName() {
        return "workspace_write";
    }

    @Override
    public String getDescription() {
        return withWorkspaceHint(
                "向会话工作区写入文本文件。会覆盖已存在文件。"
                        + "path 可为绝对路径或相对工作区根的相对路径。"
                        + "优先编辑已有文件而不是新建文档；不要用 shell echo/heredoc 代替本工具。"
        );
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("path", Map.of("type", "string", "description", "目标文件路径（绝对或相对工作区根）"));
        properties.put("content", Map.of("type", "string", "description", "要写入的完整文件内容"));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("path", "content"));
        return parameters;
    }

    @Override
    public Object execute(Object input) {
        try {
            Map<String, Object> params = requireInputMap(input);
            Path workspaceRoot = requireWorkspaceRoot();
            Path filePath = requireWritablePath(params);
            Object contentValue = params.get("content");
            if (contentValue == null) {
                return "content is required";
            }
            String content = String.valueOf(contentValue);
            if (content.length() > workspaceRuntimeOptions.getMaxWriteChars()) {
                return "content 超过最大写入字符数 " + workspaceRuntimeOptions.getMaxWriteChars();
            }

            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(filePath, content, StandardCharsets.UTF_8);
            if (agentContext != null) {
                long mtimeMs = Files.getLastModifiedTime(filePath).toMillis();
                agentContext.markWorkspaceFileRead(WorkspaceFileReadState.builder()
                        .absolutePath(filePath.toAbsolutePath().normalize().toString())
                        .mtimeMs(mtimeMs)
                        .startLine(1)
                        .lineCount(Integer.MAX_VALUE)
                        .contentHash(WorkspaceReadStateStore.sha256Hex(content))
                        .build());
                // 写计划文件时同步 PlanModeState（ExitPlanMode / UI 一致）
                String relative = toRelativePath(workspaceRoot, filePath);
                if (relative != null
                        && relative.replace('\\', '/').endsWith(org.wwz.ai.domain.agent.runtime.planmode.PlanArtifactStore.RELATIVE_PLAN_PATH)
                        && agentContext.getPlanModeState() != null) {
                    agentContext.getPlanModeState().setPlan(
                            content,
                            filePath.toAbsolutePath().normalize().toString());
                }
            }

            String relativePath = toRelativePath(workspaceRoot, filePath);
            String syncNote = syncToFileService(relativePath, content);
            return "已写入文件: " + filePath + " (" + content.length() + " chars)"
                    + (StringUtils.isBlank(syncNote) ? "" : "\n" + syncNote);
        } catch (WorkspaceAccessException e) {
            log.warn("{} workspace_write failed, input={}", requestId(), input, e);
            return e.getMessage();
        } catch (IOException e) {
            log.error("{} workspace_write io error, input={}", requestId(), input, e);
            return "workspace_write failed to write file";
        } catch (Exception e) {
            log.error("{} workspace_write error, input={}", requestId(), input, e);
            return "workspace_write execute failed";
        }
    }

    /**
     * 本地写成功后同步远端；失败只附加提示，不回滚本地文件。
     */
    private String syncToFileService(String relativePath, String content) {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            return null;
        }
        try {
            ReactorConfig reactorConfig = agentContext.getRuntimeDependencies().requireReactorConfig();
            FileArtifactPort fileArtifactPort = agentContext.getRuntimeDependencies().requireFileArtifactPort();
            if (reactorConfig == null || StringUtils.isBlank(reactorConfig.getCodeInterpreterUrl())) {
                return null;
            }

            String uploadName = relativePath == null ? "workspace-file.md" : relativePath.replace('\\', '/');
            // 文件服务侧通常按扁平文件名索引；保留 basename，把相对路径放 description
            String baseName = Path.of(uploadName).getFileName().toString();
            if (StringUtils.isBlank(baseName)) {
                baseName = "workspace-file.md";
            }
            if (!baseName.contains(".")) {
                baseName = baseName + ".md";
            }

            FileRequest fileRequest = FileRequest.builder()
                    .requestId(agentContext.getSessionId())
                    .fileName(baseName)
                    .description("workspace:" + uploadName)
                    .content(content)
                    .build();
            FileResponse fileResponse = fileArtifactPort.upload(reactorConfig.getCodeInterpreterUrl(), fileRequest);
            if (fileResponse == null) {
                return "远端同步失败: empty response";
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
                resultMap.put("command", "写入文件");
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
            return "已同步文件服务: " + fileResponse.getOssUrl();
        } catch (Exception e) {
            log.warn("{} workspace_write sync failed, path={}", requestId(), relativePath, e);
            return "远端同步失败: " + e.getMessage();
        }
    }
}
