package org.wwz.ai.domain.agent.runtime.artifact;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.TaskSummaryResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 终答正文 + {@code $$$} + artifactKey 列表的统一协议（Summary / React 直出共用）。
 * <pre>
 * 面向用户的总结文本
 * $$$
 * toolCallId::fileName、toolCallId::fileName2
 * </pre>
 */
public final class TaskSummaryArtifactProtocol {

    private static final Pattern ARTIFACT_SPLIT_PATTERN =
            Pattern.compile(ToolArtifactFormatter.ARTIFACT_KEY_SEPARATOR_REGEX);

    private TaskSummaryArtifactProtocol() {
    }

    /**
     * 写入 system 的协议说明（与 Summary 历史口径一致）。
     */
    public static String protocolInstruction() {
        return "如果需要返回最终交付文件，请在 " + ToolArtifactFormatter.ARTIFACT_DELIMITER
                + " 后仅输出 artifactKey 列表。"
                + "artifactKey 格式必须为 toolCallId" + ToolArtifactFormatter.ARTIFACT_KEY_SEPARATOR
                + "fileName，多个使用、分隔，禁止只输出 fileName。"
                + "如果没有需要返回的文件，则不要输出 " + ToolArtifactFormatter.ARTIFACT_DELIMITER + " 段落。";
    }

    /**
     * 解析终答正文；按 artifactKey 从可见产物中勾选交付文件。
     */
    public static TaskSummaryResult parse(String llmResponse, List<ToolArtifactBinding> visibleBindings) {
        if (StringUtils.isEmpty(llmResponse)) {
            return TaskSummaryResult.builder().taskSummary("").build();
        }

        String[] parts = llmResponse.split(Pattern.quote(ToolArtifactFormatter.ARTIFACT_DELIMITER), 2);
        if (parts.length < 2) {
            return TaskSummaryResult.builder().taskSummary(parts[0].trim()).build();
        }

        String summary = parts[0].trim();
        String artifactKeys = parts[1].trim();
        if (CollectionUtils.isEmpty(visibleBindings)) {
            return TaskSummaryResult.builder().taskSummary(summary).build();
        }

        Map<String, ToolArtifactBinding> keyToBinding = buildArtifactKeyIndex(visibleBindings);
        Map<String, File> selectedFiles = new LinkedHashMap<>();
        for (String item : splitArtifactItems(artifactKeys)) {
            if (StringUtils.isBlank(item)) {
                continue;
            }
            for (Map.Entry<String, ToolArtifactBinding> entry : keyToBinding.entrySet()) {
                if (item.contains(entry.getKey())) {
                    selectedFiles.putIfAbsent(entry.getKey(), entry.getValue().getFile());
                    break;
                }
            }
        }
        return TaskSummaryResult.builder()
                .taskSummary(summary)
                .files(new ArrayList<>(selectedFiles.values()))
                .build();
    }

    private static Map<String, ToolArtifactBinding> buildArtifactKeyIndex(List<ToolArtifactBinding> bindings) {
        Map<String, ToolArtifactBinding> index = new LinkedHashMap<>();
        for (ToolArtifactBinding binding : bindings) {
            String key = ToolArtifactFormatter.buildArtifactKey(binding);
            if (StringUtils.isNotBlank(key)) {
                index.put(key, binding);
            }
        }
        return index;
    }

    private static List<String> splitArtifactItems(String artifactKeys) {
        if (StringUtils.isBlank(artifactKeys)) {
            return List.of();
        }
        String[] parts = ARTIFACT_SPLIT_PATTERN.split(artifactKeys);
        List<String> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim();
            if (StringUtils.isNotBlank(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
