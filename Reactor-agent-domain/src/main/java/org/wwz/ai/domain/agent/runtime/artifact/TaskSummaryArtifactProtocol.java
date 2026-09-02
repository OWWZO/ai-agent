package org.wwz.ai.domain.agent.runtime.artifact;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.dto.TaskSummaryResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 终答正文原样透传；{@code $$$} 点名只抽 keys，不在后端对工作区文件。
 * 展示卡片由前端用会话文件表按相对路径/文件名映射。
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
                + " 后仅输出工作区相对路径或唯一文件名，多个使用、分隔。"
                + "优先写相对路径；根目录且文件名全局唯一时可以只写文件名。"
                + "如果没有需要返回的文件，则不要输出 " + ToolArtifactFormatter.ARTIFACT_DELIMITER + " 段落。";
    }

    /**
     * 终答原文保留 {@code $$$}；只解析点名 keys，不对 visibleBindings。
     */
    public static TaskSummaryResult parse(String llmResponse, List<ToolArtifactBinding> visibleBindings) {
        return parse(llmResponse);
    }

    public static TaskSummaryResult parse(String llmResponse) {
        String raw = StringUtils.trimToEmpty(llmResponse);
        if (raw.isEmpty()) {
            return TaskSummaryResult.builder().taskSummary("").build();
        }
        String[] parts = raw.split(Pattern.quote(ToolArtifactFormatter.ARTIFACT_DELIMITER), 2);
        List<String> requestedKeys = parts.length < 2 ? List.of() : splitArtifactItems(parts[1].trim());
        return TaskSummaryResult.builder()
                .taskSummary(raw)
                .artifactKeys(requestedKeys)
                .build();
    }

    /**
     * 终答交付只认 $$$ 点名；匹配与展示交给前端。
     */
    public static TaskSummaryResult resolveForDelivery(
            String llmResponse,
            List<ToolArtifactBinding> visibleBindings
    ) {
        return parse(llmResponse);
    }

    /**
     * 从终答协议构造 result payload；适用于不结束父 run 的子 Agent。
     */
    public static Map<String, Object> buildDeliveryPayload(
            String llmResponse,
            List<ToolArtifactBinding> visibleBindings
    ) {
        return toEventPayload(resolveForDelivery(llmResponse, visibleBindings));
    }

    /** 构造与实时/历史 replay 共用的 result event payload。 */
    public static Map<String, Object> toEventPayload(TaskSummaryResult result) {
        return toEventPayload(result, List.of());
    }

    /** 终答 payload 只带原文和点名 keys，不附 fileList。 */
    public static Map<String, Object> toEventPayload(
            TaskSummaryResult result,
            List<ToolArtifactBinding> visibleBindings
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskSummary", result == null ? "" : StringUtils.defaultString(result.getTaskSummary()));
        if (result != null && CollectionUtils.isNotEmpty(result.getArtifactKeys())) {
            payload.put("artifactKeys", result.getArtifactKeys());
        }
        return payload;
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
