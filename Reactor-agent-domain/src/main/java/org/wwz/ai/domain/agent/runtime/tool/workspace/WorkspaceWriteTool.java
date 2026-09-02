package org.wwz.ai.domain.agent.runtime.tool.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写入工作区文本文件。
 * 本地写入成功后，仅向文件服务登记本地路径并拿预览 URL（不重复上传 content）。
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
                return failResult("content is required");
            }
            String content = String.valueOf(contentValue);
            if (content.length() > workspaceRuntimeOptions.getMaxWriteChars()) {
                return failResult("content 超过最大写入字符数 " + workspaceRuntimeOptions.getMaxWriteChars());
            }

            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            // 写入始终使用 UTF-8，并立即刷新 readState，使同一轮后续 edit 不必重复 read。
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
                // 写计划文件时同步 PlanModeState（ExitPlanMode / UI 一致）。
                String agentPlanPath = toAgentPath(filePath);
                if (agentPlanPath != null
                        && agentPlanPath.replace('\\', '/').endsWith(org.wwz.ai.domain.agent.runtime.planmode.PlanArtifactStore.RELATIVE_PLAN_PATH)
                        && agentContext.getPlanModeState() != null) {
                    // 计划路径对 Agent/提示词只保留虚拟相对路径
                    agentContext.getPlanModeState().setPlan(content, agentPlanPath);
                }
            }

            String agentPath = toAgentPath(filePath);
            String relativePath = toRelativePath(workspaceRoot, filePath);
            WorkspaceFileRegistration.registerLocalFile(
                    agentContext, relativePath, filePath, "写入文件");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", agentPath);
            data.put("chars", content.length());
            return okResult(data);
        } catch (WorkspaceAccessException e) {
            log.warn("{} workspace_write failed, input={}", requestId(), input, e);
            return failResult(e.getMessage());
        } catch (IOException e) {
            log.error("{} workspace_write io error, input={}", requestId(), input, e);
            return failResult("workspace_write failed to write file");
        } catch (Exception e) {
            log.error("{} workspace_write error, input={}", requestId(), input, e);
            return failResult("workspace_write execute failed");
        }
    }
}
