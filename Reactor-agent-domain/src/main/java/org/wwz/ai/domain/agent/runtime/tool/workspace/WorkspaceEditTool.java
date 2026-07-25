package org.wwz.ai.domain.agent.runtime.tool.workspace;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.CodeInterpreterResponse;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.FileRequest;
import org.wwz.ai.domain.agent.runtime.dto.FileResponse;

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
 * 局部替换编辑工作区文件（对齐 cchaha Edit）。
 * 要求先 workspace_read；old_string 默认必须唯一，除非 replace_all=true。
 */
public class WorkspaceEditTool extends AbstractWorkspacePathTool {

    public WorkspaceEditTool(WorkspaceService workspaceService, WorkspaceRuntimeOptions workspaceRuntimeOptions) {
        super(workspaceService, workspaceRuntimeOptions);
    }

    @Override
    public String getName() {
        return "workspace_edit";
    }

    @Override
    public String getDescription() {
        return withWorkspaceHint(
                "对工作区文件做精确字符串替换（局部编辑）。\n"
                        + "Usage:\n"
                        + "- 编辑前必须先用 workspace_read 读取该文件；未读过会失败。\n"
                        + "- 从 read 结果复制文本时，不要包含行号前缀（形如 `12 | `），只保留真实文件内容。\n"
                        + "- old_string 必须在文件中唯一；若不唯一，请扩大上下文，或设 replace_all=true。\n"
                        + "- replace_all 适合重命名变量等批量替换。\n"
                        + "- 优先编辑已有文件；不要用本工具创建新文件（新建请用 workspace_write）。\n"
                        + "- old_string 与 new_string 不能相同。"
        );
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("path", Map.of("type", "string", "description", "要修改的文件路径（绝对或相对工作区根）"));
        properties.put("old_string", Map.of("type", "string", "description", "要被替换的原文（必须精确匹配）"));
        properties.put("new_string", Map.of("type", "string", "description", "替换后的新文本（必须与 old_string 不同）"));
        properties.put("replace_all", Map.of("type", "boolean", "description", "是否替换全部匹配项，默认 false"));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("path", "old_string", "new_string"));
        return parameters;
    }

    @Override
    public Object execute(Object input) {
        try {
            Map<String, Object> params = requireInputMap(input);
            Path filePath = requireWritablePath(params);
            if (!Files.isRegularFile(filePath)) {
                return "workspace_edit 只支持已存在的文件路径: " + filePath;
            }

            String absolutePath = filePath.toAbsolutePath().normalize().toString();
            if (agentContext == null) {
                return "workspace_edit requires agent context";
            }
            WorkspaceFileReadState readState = agentContext.getWorkspaceFileReadState(absolutePath);
            if (readState == null) {
                return "You must use workspace_read at least once on this file before editing: " + absolutePath;
            }
            long mtimeMs = Files.getLastModifiedTime(filePath).toMillis();
            String currentContent = Files.readString(filePath, StandardCharsets.UTF_8);
            String currentHash = WorkspaceReadStateStore.sha256Hex(currentContent);
            if (mtimeMs > readState.getMtimeMs()) {
                // mtime 变化时，hash 相同则放行；不同则要求重读
                if (readState.getContentHash() == null || !readState.getContentHash().equals(currentHash)) {
                    return "File has been modified since read, either by the user or another tool. "
                            + "Read it again with workspace_read before editing: " + absolutePath;
                }
            }

            Object oldValue = params.get("old_string");
            Object newValue = params.get("new_string");
            if (oldValue == null) {
                return "old_string is required";
            }
            if (newValue == null) {
                return "new_string is required";
            }
            String oldString = String.valueOf(oldValue);
            String newString = String.valueOf(newValue);
            if (oldString.equals(newString)) {
                return "old_string and new_string must be different";
            }
            if (oldString.isEmpty()) {
                return "old_string must not be empty";
            }

            boolean replaceAll = readBoolean(params, "replace_all", false);
            String original = Files.readString(filePath, StandardCharsets.UTF_8);
            if (original.length() > workspaceRuntimeOptions.getMaxWriteChars()) {
                return "file too large to edit safely, size=" + original.length();
            }

            int occurrences = countOccurrences(original, oldString);
            if (occurrences == 0) {
                return "old_string not found in file. Re-read the file with workspace_read and ensure exact match "
                        + "(do not include line-number prefixes).";
            }
            if (!replaceAll && occurrences > 1) {
                return "Found " + occurrences + " occurrences of old_string; either provide more surrounding context "
                        + "to make it unique, or set replace_all=true.";
            }

            String updated = replaceAll
                    ? original.replace(oldString, newString)
                    : original.replaceFirst(java.util.regex.Pattern.quote(oldString),
                    java.util.regex.Matcher.quoteReplacement(newString));

            Files.writeString(filePath, updated, StandardCharsets.UTF_8);
            // 编辑后刷新 readState，允许连续 edit
            long newMtime = Files.getLastModifiedTime(filePath).toMillis();
            agentContext.markWorkspaceFileRead(WorkspaceFileReadState.builder()
                    .absolutePath(absolutePath)
                    .mtimeMs(newMtime)
                    .startLine(1)
                    .lineCount(Integer.MAX_VALUE)
                    .contentHash(WorkspaceReadStateStore.sha256Hex(updated))
                    .build());

            Path workspaceRoot = requireWorkspaceRoot();
            String relativePath = toRelativePath(workspaceRoot, filePath);
            String syncNote = syncToFileService(relativePath, updated);

            StringBuilder result = new StringBuilder();
            result.append("已编辑文件: ").append(filePath).append('\n');
            result.append("替换次数: ").append(replaceAll ? occurrences : 1).append('\n');
            result.append("字符变化: ").append(original.length()).append(" -> ").append(updated.length());
            if (StringUtils.isNotBlank(syncNote)) {
                result.append('\n').append(syncNote);
            }
            return result.toString();
        } catch (WorkspaceAccessException e) {
            log.warn("{} workspace_edit failed, input={}", requestId(), input, e);
            return e.getMessage();
        } catch (IOException e) {
            log.error("{} workspace_edit io error, input={}", requestId(), input, e);
            return "workspace_edit failed to edit file";
        } catch (Exception e) {
            log.error("{} workspace_edit error, input={}", requestId(), input, e);
            return "workspace_edit execute failed";
        }
    }

    private int countOccurrences(String content, String target) {
        int count = 0;
        int index = 0;
        while (true) {
            int found = content.indexOf(target, index);
            if (found < 0) {
                return count;
            }
            count++;
            index = found + Math.max(1, target.length());
        }
    }

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
                resultMap.put("command", "编辑文件");
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
            log.warn("{} workspace_edit sync failed, path={}", requestId(), relativePath, e);
            return "远端同步失败: " + e.getMessage();
        }
    }
}
