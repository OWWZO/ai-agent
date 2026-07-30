package org.wwz.ai.domain.agent.runtime.tool.workspace;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 局部替换编辑工作区文件（对齐 cchaha Edit）。
 * 要求先 workspace_read；old_string 默认必须唯一，除非 replace_all=true。
 * 本地编辑成功后仅登记预览 URL，不重复上传 content。
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
                return failResult("workspace_edit 只支持已存在的文件路径: " + filePath);
            }

            String absolutePath = filePath.toAbsolutePath().normalize().toString();
            if (agentContext == null) {
                return failResult("workspace_edit requires agent context");
            }
            WorkspaceFileReadState readState = agentContext.getWorkspaceFileReadState(absolutePath);
            if (readState == null) {
                return failResult("You must use workspace_read at least once on this file before editing: " + absolutePath);
            }
            long mtimeMs = Files.getLastModifiedTime(filePath).toMillis();
            String currentContent = Files.readString(filePath, StandardCharsets.UTF_8);
            String currentHash = WorkspaceReadStateStore.sha256Hex(currentContent);
            if (mtimeMs > readState.getMtimeMs()) {
                // mtime 变化时，hash 相同则放行；不同则要求重读
                if (readState.getContentHash() == null || !readState.getContentHash().equals(currentHash)) {
                    return failResult("File has been modified since read, either by the user or another tool. "
                            + "Read it again with workspace_read before editing: " + absolutePath);
                }
            }

            Object oldValue = params.get("old_string");
            Object newValue = params.get("new_string");
            if (oldValue == null) {
                return failResult("old_string is required");
            }
            if (newValue == null) {
                return failResult("new_string is required");
            }
            String oldString = String.valueOf(oldValue);
            String newString = String.valueOf(newValue);
            if (oldString.equals(newString)) {
                return failResult("old_string and new_string must be different");
            }
            if (oldString.isEmpty()) {
                return failResult("old_string must not be empty");
            }

            boolean replaceAll = readBoolean(params, "replace_all", false);
            String original = Files.readString(filePath, StandardCharsets.UTF_8);
            if (original.length() > workspaceRuntimeOptions.getMaxWriteChars()) {
                return failResult("file too large to edit safely, size=" + original.length());
            }

            int occurrences = countOccurrences(original, oldString);
            if (occurrences == 0) {
                return failResult("old_string not found in file. Re-read the file with workspace_read and ensure exact match "
                        + "(do not include line-number prefixes).");
            }
            if (!replaceAll && occurrences > 1) {
                return failResult("Found " + occurrences + " occurrences of old_string; either provide more surrounding context "
                        + "to make it unique, or set replace_all=true.");
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
            String registerNote = WorkspaceFileRegistration.registerLocalFile(
                    agentContext, relativePath, filePath, "编辑文件");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", filePath.toString());
            data.put("replacements", replaceAll ? occurrences : 1);
            data.put("charsBefore", original.length());
            data.put("charsAfter", updated.length());
            if (StringUtils.isNotBlank(registerNote)) {
                data.put("registerNote", registerNote);
            }
            return okResult(data);
        } catch (WorkspaceAccessException e) {
            log.warn("{} workspace_edit failed, input={}", requestId(), input, e);
            return failResult(e.getMessage());
        } catch (IOException e) {
            log.error("{} workspace_edit io error, input={}", requestId(), input, e);
            return failResult("workspace_edit failed to edit file");
        } catch (Exception e) {
            log.error("{} workspace_edit error, input={}", requestId(), input, e);
            return failResult("workspace_edit execute failed");
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
}
